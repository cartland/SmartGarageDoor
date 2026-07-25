#!/usr/bin/env ruby
# frozen_string_literal: true

# Print recent TestFlight crash-feedback submissions (and their crash logs)
# from App Store Connect. Ruby stdlib only — no gems, so it runs identically
# on any CI runner.
#
# WHY: TestFlight offers testers a "share with the developer" prompt after a
# crash. Shared submissions land in App Store Connect as
# `betaFeedbackCrashSubmissions` — the ONLY crash telemetry reachable without
# Xcode Organizer. The deploy-capable ASC API key lives only in GitHub Actions
# secrets, so this runs via .github/workflows/ios-crash-feedback.yml
# (workflow_dispatch), never on a dev machine.
#
# NOTE: this surfaces only crashes a tester chose to share. General TestFlight
# crash telemetry (no tester action) is visible only in Xcode Organizer.
#
# Env:
#   ASC_KEY_ID          App Store Connect API Key ID
#   ASC_ISSUER_ID       App Store Connect API Issuer ID
#   ASC_KEY_PATH        path to AuthKey_<KEYID>.p8 (unencrypted EC private key, PEM)
#   ASC_BUNDLE_ID       app bundle id (default: com.chriscartland.garage)
#   ASC_FEEDBACK_LIMIT  max submissions to print (default: 10)
#
# Exits 0 on success (including "no submissions"), 2 on any error. The API
# resource shapes are newer (2025) than the rest of our ASC usage; where a
# shape differs from expectations this prints the raw JSON (capped) instead of
# failing, so the output is always diagnostically useful.

require 'openssl'
require 'base64'
require 'json'
require 'net/http'
require 'uri'

def die(msg)
  warn("asc-crash-feedback: #{msg}")
  exit 2
end

key_id    = ENV['ASC_KEY_ID']    || die('ASC_KEY_ID not set')
issuer_id = ENV['ASC_ISSUER_ID'] || die('ASC_ISSUER_ID not set')
key_path  = ENV['ASC_KEY_PATH']  || die('ASC_KEY_PATH not set')
bundle_id = ENV['ASC_BUNDLE_ID'] || 'com.chriscartland.garage'
limit     = (ENV['ASC_FEEDBACK_LIMIT'] || '10').to_i.clamp(1, 50)

die("key file not found: #{key_path}") unless File.file?(key_path)

def b64(bin)
  Base64.urlsafe_encode64(bin, padding: false)
end

# --- ES256 JWT for the App Store Connect API (same recipe as asc-latest-build.rb) ---
begin
  pkey = OpenSSL::PKey.read(File.read(key_path))
rescue StandardError => e
  die("could not read EC private key: #{e.message}")
end

header  = { alg: 'ES256', kid: key_id, typ: 'JWT' }
payload = { iss: issuer_id, iat: Time.now.to_i, exp: Time.now.to_i + 1200, aud: 'appstoreconnect-v1' }
signing_input = "#{b64(header.to_json)}.#{b64(payload.to_json)}"

der  = pkey.sign(OpenSSL::Digest.new('SHA256'), signing_input)
asn1 = OpenSSL::ASN1.decode(der)
r = asn1.value[0].value.to_s(2).rjust(32, "\x00".b)
s = asn1.value[1].value.to_s(2).rjust(32, "\x00".b)
jwt = "#{signing_input}.#{b64(r + s)}"

# GET a URL (ASC-relative path or absolute URL, e.g. a signed crash-log CDN
# link). Auth header only for the ASC host.
def http_get(path_or_url, jwt)
  uri = path_or_url.start_with?('http') ? URI(path_or_url) : URI("https://api.appstoreconnect.apple.com#{path_or_url}")
  req = Net::HTTP::Get.new(uri)
  req['Authorization'] = "Bearer #{jwt}" if uri.host == 'api.appstoreconnect.apple.com'
  res = Net::HTTP.start(uri.host, uri.port, use_ssl: true, open_timeout: 15, read_timeout: 60) do |http|
    http.request(req)
  end
  [res.code.to_i, res.body.to_s]
end

# --- Resolve the app id ---
code, body = http_get("/v1/apps?filter%5BbundleId%5D=#{URI.encode_www_form_component(bundle_id)}&limit=1", jwt)
die("apps query failed (HTTP #{code}): #{body[0, 300]}") unless code == 200
apps = JSON.parse(body)
die("no app found for bundleId #{bundle_id}") if apps['data'].nil? || apps['data'].empty?
app_id = apps['data'][0]['id']

# --- List crash-feedback submissions, newest first, with build info ---
code, body = http_get(
  "/v1/apps/#{app_id}/betaFeedbackCrashSubmissions?limit=#{limit}&sort=-createdDate&include=build&fields%5Bbuilds%5D=version",
  jwt
)
die("betaFeedbackCrashSubmissions query failed (HTTP #{code}): #{body[0, 500]}") unless code == 200

doc = JSON.parse(body)
submissions = doc['data'] || []
builds_by_id = {}
(doc['included'] || []).each do |inc|
  builds_by_id[inc['id']] = inc.dig('attributes', 'version') if inc['type'] == 'builds'
end

if submissions.empty?
  puts "No TestFlight crash-feedback submissions found for #{bundle_id}."
  puts '(Testers must tap "Share" on the TestFlight crash prompt for a crash to appear here;'
  puts 'unshared crash telemetry is visible only in Xcode Organizer.)'
  exit 0
end

puts "#{submissions.length} crash-feedback submission(s) for #{bundle_id} (newest first):"
submissions.each_with_index do |sub, i|
  attrs = sub['attributes'] || {}
  build_id = sub.dig('relationships', 'build', 'data', 'id')
  build_version = builds_by_id[build_id] || build_id || '?'
  puts ''
  puts "=== [#{i + 1}] submission #{sub['id']} ==="
  puts "  created:  #{attrs['createdDate']}"
  puts "  build:    #{build_version} (ios/#{build_version} if tagged)"
  %w[deviceModel osVersion appPlatform locale comment email architecture connectionType].each do |k|
    puts "  #{k}: #{attrs[k]}" unless attrs[k].nil?
  end
  # Any attributes not covered above still get printed (shape-tolerant).
  extra = attrs.reject { |k, _| %w[createdDate deviceModel osVersion appPlatform locale comment email architecture connectionType].include?(k) }
  puts "  other:    #{extra.to_json[0, 600]}" unless extra.empty?

  # --- Crash log: fetch the related resource, then follow any URL in it ---
  code, body = http_get("/v1/betaFeedbackCrashSubmissions/#{sub['id']}/crashLog", jwt)
  if code != 200
    puts "  crashLog: unavailable (HTTP #{code}): #{body[0, 300]}"
    next
  end
  log_doc = JSON.parse(body)
  log_attrs = log_doc.dig('data', 'attributes') || {}
  inline_text = log_attrs['logText'] || log_attrs['crashLog'] || log_attrs['content']
  url = log_attrs.values.find { |v| v.is_a?(String) && v.start_with?('http') }
  if inline_text
    puts '  --- crash log (inline, first 200 lines) ---'
    puts inline_text.lines.first(200).join
    puts '  --- end crash log ---'
  elsif url
    dl_code, dl_body = http_get(url, jwt)
    if dl_code == 200
      puts '  --- crash log (downloaded, first 200 lines) ---'
      puts dl_body.lines.first(200).join
      puts '  --- end crash log ---'
    else
      puts "  crashLog download failed (HTTP #{dl_code}); raw resource: #{log_doc.to_json[0, 800]}"
    end
  else
    puts "  crashLog resource had no recognizable text/url; raw: #{log_doc.to_json[0, 800]}"
  end
end
