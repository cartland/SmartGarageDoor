
function getDoorStatus(observations, sensorClosedName, sensorOpenName) {
  const recent = getRecentDoorData(observations, sensorClosedName, sensorOpenName);
  const nowMillis = getCurrentUtcTimeMillis();
  const output = {
    closed: false,
    state: STATE_UNKNOWN,
    messages: '',
    lastUpdatedMillis: null,
  };
  console.log(recent);
  const closedSensorHistory = getHistoryWithDurations(recent.sensorClosedData, nowMillis);
  if (closedSensorHistory.length <= 0) {
    console.error('No closed sensor history');
    return output;
  }
  const confirmedClosed = closedSensorHistory[0].value == 0;
  if (confirmedClosed) {
    output.state = STATE_CLOSED;
    output.closed = true;
  } else {
    output.state = STATE_OPEN;
    output.closed = false;
  }
  const lastUpdatedMillis = closedSensorHistory[0].timeMilliseconds;
  output.lastUpdatedMillis = lastUpdatedMillis;
  const d = new Date(0);
  d.setUTCMilliseconds(lastUpdatedMillis);
  const lastUpdatedString = myDateFormat(d, true);
  output.messages = [
    'Last updated ' + lastUpdatedString,
  ];

  const openSensorHistory = getHistoryWithDurations(recent.sensorOpenData, nowMillis);
  console.log(openSensorHistory);
  if (openSensorHistory.length <= 0) {
    return output;
  }
  const confirmedOpen = openSensorHistory[0].value == 0;
  if (confirmedOpen) {
    if (confirmedClosed) {
      output.state = STATE_ERROR_BOTH_SENSORS_ACTIVE;
    } else {
      // Confirmed closed.
    }
  } else {
    if (confirmedClosed) {
      // Confirmed open.
    } else {
      output.state = STATE_OPEN_UNCONFIRMED;
    }
  }
  const openSensorLastUpdatedMillis = openSensorHistory[0].timeMilliseconds;
  output.openSensorLastUpdatedMillis = openSensorLastUpdatedMillis;
  return output;
}

function getHistoryWithDurations(data, nowMillis) {
  if (!data) {
    console.error('getHistoryWithDurations: No data');
    return [];
  }
  const history = [];
  let timeMillis = nowMillis;
  for (const datum of data) {
    const newDatum = {
      durationMillis: timeMillis - datum.timeMilliseconds,
    };
    Object.assign(newDatum, datum);
    history.push(newDatum);
    timeMillis = newDatum.timeMilliseconds;
  }
  return history;
}

function getRecentDoorData(observations, sensorClosedName, sensorOpenName) {
  const sensorClosedData = [];
  const sensorOpenData = [];
  for (const session of Object.keys(observations)) {
    const sessionObservations = observations[session];
    const numberOfPointsInSession = sessionObservations.length;
    for (let observationIndex = 0; observationIndex < numberOfPointsInSession; observationIndex++) {
      const sessionObservation = sessionObservations[observationIndex];
      const utcSeconds = sessionObservation.utcSeconds;
      if (sensorClosedName in sessionObservation) {
        const sensorClosedValue = sessionObservation[sensorClosedName];
        const closedPoint = {
          timeMilliseconds: utcSeconds * 1000,
          value: sensorClosedValue,
          sensorName: sensorClosedName,
        };
        sensorClosedData.push(closedPoint);
      }
      if (sensorOpenName in sessionObservation) {
        const sensorOpenValue = sessionObservation[sensorOpenName];
        const openPoint = {
          timeMilliseconds: utcSeconds * 1000,
          value: sensorOpenValue,
          sensorName: sensorOpenName,
        };
        sensorOpenData.push(openPoint);
      }
    }
    sensorClosedData.sort((a, b) => {
      return b.timeMilliseconds - a.timeMilliseconds;
    });
    sensorOpenData.sort((a, b) => {
      return b.timeMilliseconds - a.timeMilliseconds;
    });
  }
  return {
    sensorClosedData: sensorClosedData,
    sensorOpenData: sensorOpenData,
  }
}
