#!/usr/bin/env ruby
# frozen_string_literal: true

# Print the highest CFBundleVersion (build number) currently on App Store Connect
# for the app, or 0 if there are none. Ruby stdlib only — no gems, so it runs
# identically on the macOS CI runner and a local Mac.
#
# Shared by:
#   - .github/workflows/release-ios.yml  (pre-flight: abort if the tag's build
#     number is not strictly greater than this)
#   - scripts/release-ios.sh             (compute the next ios/N tag = this + 1,
#     so the tag always equals the next free CFBundleVersion)
#
# Env:
#   ASC_KEY_ID      App Store Connect API Key ID
#   ASC_ISSUER_ID   App Store Connect API Issuer ID
#   ASC_KEY_PATH    path to AuthKey_<KEYID>.p8 (unencrypted EC private key, PEM)
#   ASC_BUNDLE_ID   app bundle id (default: com.chriscartland.garage)
#
# Prints the integer to stdout and exits 0 on success. On ANY error (missing
# creds, key unreadable, API failure, app not found) it prints a diagnostic to
# stderr and exits 2 — so callers can distinguish "0 builds exist" (stdout "0",
# exit 0) from "couldn't check" (exit 2).
#
# Build numbers are assumed integer (the release sets CURRENT_PROJECT_VERSION=N).
# If a CFBundleVersion is dotted (e.g. "1.2.3"), its leading integer is used.

require 'openssl'
require 'base64'
require 'json'
require 'net/http'
require 'uri'

def die(msg)
  warn("asc-latest-build: #{msg}")
  exit 2
end

key_id    = ENV['ASC_KEY_ID']    || die('ASC_KEY_ID not set')
issuer_id = ENV['ASC_ISSUER_ID'] || die('ASC_ISSUER_ID not set')
key_path  = ENV['ASC_KEY_PATH']  || die('ASC_KEY_PATH not set')
bundle_id = ENV['ASC_BUNDLE_ID'] || 'com.chriscartland.garage'

die("key file not found: #{key_path}") unless File.file?(key_path)

def b64(bin)
  Base64.urlsafe_encode64(bin, padding: false)
end

# --- ES256 JWT for the App Store Connect API (hand-rolled, stdlib only) ---
begin
  pkey = OpenSSL::PKey.read(File.read(key_path))
rescue StandardError => e
  die("could not read EC private key: #{e.message}")
end

header  = { alg: 'ES256', kid: key_id, typ: 'JWT' }
payload = { iss: issuer_id, iat: Time.now.to_i, exp: Time.now.to_i + 1200, aud: 'appstoreconnect-v1' }
signing_input = "#{b64(header.to_json)}.#{b64(payload.to_json)}"

der  = pkey.sign(OpenSSL::Digest.new('SHA256'), signing_input) # DER ECDSA sig
asn1 = OpenSSL::ASN1.decode(der)
# JOSE wants raw r||s, each left-padded to 32 bytes for P-256.
r = asn1.value[0].value.to_s(2).rjust(32, "\x00".b)
s = asn1.value[1].value.to_s(2).rjust(32, "\x00".b)
jwt = "#{signing_input}.#{b64(r + s)}"

def asc_get(path, jwt)
  uri = URI("https://api.appstoreconnect.apple.com#{path}")
  req = Net::HTTP::Get.new(uri)
  req['Authorization'] = "Bearer #{jwt}"
  res = Net::HTTP.start(uri.host, uri.port, use_ssl: true, open_timeout: 15, read_timeout: 30) do |http|
    http.request(req)
  end
  [res.code.to_i, res.body.to_s]
end

# --- Resolve the app id from the bundle id ---
code, body = asc_get("/v1/apps?filter%5BbundleId%5D=#{URI.encode_www_form_component(bundle_id)}&limit=1", jwt)
die("apps query failed (HTTP #{code}): #{body[0, 300]}") unless code == 200
apps = JSON.parse(body)
die("no app found for bundleId #{bundle_id}") if apps['data'].nil? || apps['data'].empty?
app_id = apps['data'][0]['id']

# --- Walk all builds, return the max integer CFBundleVersion ---
max_build = 0
path = "/v1/builds?filter%5Bapp%5D=#{app_id}&limit=200&fields%5Bbuilds%5D=version"
loop do
  code, body = asc_get(path, jwt)
  die("builds query failed (HTTP #{code}): #{body[0, 300]}") unless code == 200
  data = JSON.parse(body)
  (data['data'] || []).each do |build|
    version = build.dig('attributes', 'version').to_s
    leading = version[/\A\d+/]
    n = leading ? leading.to_i : 0
    max_build = n if n > max_build
  end
  nxt = data.dig('links', 'next')
  break unless nxt

  path = nxt.sub('https://api.appstoreconnect.apple.com', '')
end

puts max_build
