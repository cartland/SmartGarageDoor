const COLOR_OPACITY = 'AA'; // Slightly transparent.
const COLORS = [
  '#E74C3C' + COLOR_OPACITY,
  '#9B59B6' + COLOR_OPACITY,
  '#3498DB' + COLOR_OPACITY,
  '#1ABC9C' + COLOR_OPACITY,
  '#F4D03F' + COLOR_OPACITY,
  '#E67E22' + COLOR_OPACITY,
];


function NN(number) {
  if (number < 10) {
    return '0' + number;
  }
  return '' + number;
}

/**
 * Formats date and time. Time must be provided in local timezone.
 * YYYY-MM-DD hh:mm
 */
function myDateFormat(date, seconds) {
  var DD = NN(date.getDate());
  var MM = NN(date.getMonth() + 1);
  var YYYY = date.getFullYear();
  var hh = NN(date.getHours());
  var min = NN(date.getMinutes());
  var ss = NN(date.getSeconds());
  if (seconds) {
    return YYYY + '-' + MM + '-' + DD + ' ' + hh + ':' + min + ':' + ss;
  }
  return YYYY + '-' + MM + '-' + DD + ' ' + hh + ':' + min;
}

/**
 * Convert UTC milliseconds to local timezone Date object.
 */
function utcMillisecondsToLocalDateFormat(milliseconds, showSeconds) {
  const x = new Date(0);
  x.setUTCMilliseconds(milliseconds + 60 * x.getTimezoneOffset());
  return myDateFormat(x, showSeconds);
}

function getCurrentUtcTimeMillis() {
  return new Date().getTime();
}

function getDurationFromMillis(millis) {
  const seconds = Math.floor((millis / 1000) % 60);
  const minutes = Math.floor((millis / (1000 * 60)) % 60);
  const hours = Math.floor(millis / (1000 * 60 * 60));
  var hh = NN(hours);
  var min = NN(minutes);
  var ss = NN(seconds);
  return hh + ':' + min + ':' + ss;
}

function getTimeAxisLabel() {
  const hourOffset = -(new Date(0).getTimezoneOffset() / 60);
  if (hourOffset < 0) {
    return 'Time (GMT' + hourOffset + ')';
  }
  return 'Time (GMT+' + hourOffset + ')';
}

function getDateRange(observations) {
  let minUtcSeconds = undefined;
  let maxUtcSeconds = undefined;
  for (const session of Object.keys(observations)) {
    const sessionObservations = observations[session];
    for (let observationIndex = 0; observationIndex < sessionObservations.length; observationIndex++) {
      const utcSeconds = sessionObservations[observationIndex].utcSeconds;
      if (typeof minUtcSeconds === 'undefined') {
        minUtcSeconds = utcSeconds;
      }
      if (typeof maxUtcSeconds === 'undefined') {
        maxUtcSeconds = utcSeconds;
      }
      if (utcSeconds < minUtcSeconds) {
        minUtcSeconds = utcSeconds;
      }
      if (utcSeconds > maxUtcSeconds) {
        maxUtcSeconds = utcSeconds;
      }
    }
  }
  return {
    minUtcSeconds: minUtcSeconds,
    maxUtcSeconds: maxUtcSeconds,
  };
}

function getGraphStepSize(utcSecondsRange) {
  const SEC = 1000;
  const MIN = 60 * SEC;
  const TEN_MIN = 10 * MIN;
  const THIRTY_MIN = 30 * MIN;
  const HOUR = 60 * MIN;
  const TWO_HOUR = 2 * HOUR;
  const THREE_HOUR = 3 * HOUR;
  const SIX_HOUR = 6 * HOUR;
  const TWELVE_HOUR = 12 * HOUR;
  const DAY = 24 * HOUR;
  const TWO_DAY = 2 * DAY;
  const THREE_DAY = 3 * DAY;
  const WEEK = 7 * DAY;
  const OPTIONS = [SEC, MIN, TEN_MIN, THIRTY_MIN,
    HOUR, TWO_HOUR, THREE_HOUR, SIX_HOUR, TWELVE_HOUR,
    DAY, TWO_DAY, THREE_HOUR, WEEK];
  const utcMillisecondsRange = 1000 * utcSecondsRange;
  for (let i = 0; i < OPTIONS.length; i++) {
    const option = OPTIONS[i];
    if (utcMillisecondsRange < option * 6) {
      console.log('Graph Step Size (ms)', option);
      return option;
    }
  }
  return OPTIONS[OPTIONS.length - 1];
}

function abbreviateSession(session) {
  return session.substring(0, 6);
}
