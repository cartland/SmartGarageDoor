
function parseObservations(doc) {
  const observations = {};
  for (let lineNumber = 0; lineNumber < doc.docs.length; lineNumber++) {
    const line = doc.docs[lineNumber];
    const data = line.data();
    // Check to make sure all of the relevant data fields exist.
    if (!('session' in data)
      || !('FIRESTORE_databaseTimestamp' in data)
      || !('seconds' in data['FIRESTORE_databaseTimestamp'])
      || !('queryParams' in data)
    ) {
      // Skip data that is missing necessary fields.
      continue;
    }
    const session = data['session'];
    if (!(session in observations)) {
      observations[session] = [];
    }
    const utcSeconds = parseInt(data['FIRESTORE_databaseTimestamp']['seconds']);
    const parsedData = {
      utcSeconds: utcSeconds
    };
    if ('batteryVoltage' in data['queryParams']) {
      parsedData['batteryVoltage'] = parseFloat(data['queryParams']['batteryVoltage']);
    }
    if ('sensorA' in data['queryParams']) {
      parsedData['sensorA'] = parseFloat(data['queryParams']['sensorA']);
    }
    if ('sensorB' in data['queryParams']) {
      parsedData['sensorB'] = parseFloat(data['queryParams']['sensorB']);
    }
    observations[session].push(parsedData);
  }
  for (const session of Object.keys(observations)) {
    observations[session].sort((a, b) => {
      return a['utcSeconds'] - b['utcSeconds'];
    });
  }
  return observations;
}
