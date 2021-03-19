
function getDoorStatus(observations, sensorClosedName, sensorOpenName) {
  const recent = getRecentDoorData(observations, sensorClosedName, sensorOpenName);
  const nowMillis = getCurrentUtcTimeMillis();
  const output = {
    closed: false,
    state: STATE_UNKNOWN,
    message: '',
  };
  const closedSensorHistory = getHistoryWithDurations(recent.sensorClosedData, nowMillis);
  if (closedSensorHistory.length <= 0) {
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
  return output;
}

function getHistoryWithDurations(data, nowMillis) {
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
