
function createSensorChart(ctx, title) {
  return new Chart(ctx, {
    type: 'line',
    data: {
      datasets: []
    },
    options: {
      title: {
        text: title ? title : 'Sensor Data Over Time by Session and Sensor',
        display: true,
        fontSize: 24,
      },
      scales: {
        xAxes: [{
          scaleLabel: {
            display: true,
            labelString: getTimeAxisLabel(),
            fontSize: 24,
          },
          type: 'linear',
          position: 'bottom',
          ticks: {
            callback: (value, index, values) => {
              const milliseconds = value;
              const showSeconds = false;
              return utcMillisecondsToLocalDateFormat(milliseconds, showSeconds);
            }
          }
        }],
        yAxes: [{
          scaleLabel: {
            display: true,
            labelString: 'Sensor Value',
            fontSize: 24,
          },
        }]
      },
      tooltips: {
        titleAlign: 'center',
        callbacks: {
          label: function (tooltipItem, data) {
            return data.datasets[tooltipItem.datasetIndex].label;
          },
          title: function (tooltipItem, data) {
            const item = tooltipItem[0];
            return [
              '' + item.value,
            ];
          },
          footer: function (tooltipItem, data) {
            const item = tooltipItem[0];
            const showSeconds = true;
            return [
              utcMillisecondsToLocalDateFormat(item.xLabel, showSeconds),
            ];
          },
          labelColor: function (tooltipItem, data) {
            const color = COLORS[tooltipItem.datasetIndex % COLORS.length];
            return {
              borderColor: color,
              backgroundColor: color,
            };
          },
        }
      },
      onClick: (arr, elements) => {
        console.log(arr, elements);
      },
    }
  });
}

const MIN_SENSOR_COUNT = -1;
const MIN_SENSOR_TIME_RANGE_SECONDS = -1;
function filterSensorObservations(observations, sensorName) {
  const filteredSensorObservations = {};
  for (const session of Object.keys(observations)) {
    const sessionObservations = observations[session];
    const numberOfPointsInSession = sessionObservations.length;
    if (MIN_SENSOR_COUNT > 0 && numberOfPointsInSession < MIN_SENSOR_COUNT) {
      console.debug('Skipping sensor session', session,
        ' because there are fewer than', MIN_SENSOR_COUNT, 'data points');
      continue;
    }
    const firstObservation = sessionObservations[0];
    const firstUtcSeconds = firstObservation.utcSeconds;
    const lastObservation = sessionObservations[sessionObservations.length - 1];
    const lastUtcSeconds = lastObservation.utcSeconds;
    if (MIN_SENSOR_TIME_RANGE_SECONDS > 0 && lastUtcSeconds - firstUtcSeconds < MIN_SENSOR_TIME_RANGE_SECONDS) {
      console.debug('Skipping sensor session', session,
        ' because first and last are less than', MIN_SENSOR_TIME_RANGE_SECONDS, 'seconds apart');
      continue;
    }
    outputObservations = [];
    for (let observationIndex = 0; observationIndex < numberOfPointsInSession; observationIndex++) {
      if (!sensorName || sensorName in sessionObservations[observationIndex]) {
        outputObservations.push(sessionObservations[observationIndex]);
      }
    }
    // Do not include session if no data exists.
    if (outputObservations.length > 0) {
      filteredSensorObservations[session] = outputObservations;
    }
  }
  return filteredSensorObservations;
}

function displaySensorObservations(scatterChart, observations, sensorNameArray) {
  scatterChart.config.data.datasets = [];
  for (const sensorName of sensorNameArray) {
    for (const session of Object.keys(observations)) {
      const sessionObservations = observations[session];
      const numberOfPointsInSession = sessionObservations.length;
      const lineData = [];
      for (let observationIndex = 0; observationIndex < numberOfPointsInSession; observationIndex++) {
        const utcSeconds = sessionObservations[observationIndex].utcSeconds;
        const x = new Date(0);
        x.setUTCSeconds(utcSeconds);
        const y = sessionObservations[observationIndex][sensorName];
        const point = {
          x: x,
          y: y,
        };
        lineData.push(point);
      }
      const color = COLORS[scatterChart.config.data.datasets.length % COLORS.length];
      const label = abbreviateSession(session) + '[' + sensorName + ']';
      scatterChart.config.data.datasets.push({
        label: label,
        data: lineData,
        borderColor: color,
        backgroundColor: '#00000000',
        steppedLine: true,
      });
    }
  }
  const dateRange = getDateRange(observations);
  const utcSecondsRange = dateRange.maxUtcSeconds - dateRange.minUtcSeconds;
  scatterChart.options.scales.xAxes[0].ticks.stepSize = getGraphStepSize(utcSecondsRange);
  scatterChart.update();
}
