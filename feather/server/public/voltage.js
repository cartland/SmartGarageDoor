
function createVoltageChart(ctx) {
  return new Chart(ctx, {
    type: 'line',
    data: {
      datasets: []
    },
    options: {
      title: {
        text: 'Voltage Over Time by Session',
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
              return utcSecondsToLocalDateFormat(milliseconds, showSeconds);
            }
          }
        }],
        yAxes: [{
          scaleLabel: {
            display: true,
            labelString: 'Voltage',
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
              item.value + ' V',
            ];
          },
          footer: function (tooltipItem, data) {
            const item = tooltipItem[0];
            const showSeconds = true;
            return [
              utcSecondsToLocalDateFormat(item.xLabel, showSeconds),
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

const MIN_VOLTAGE_COUNT = -1;
const MIN_VOLTAGE_TIME_RANGE_SECONDS = -1;
function filterVoltageObservations(observations) {
  const filteredVoltageObservations = {};
  for (const session of Object.keys(observations)) {
    const sessionObservations = observations[session];
    const numberOfPointsInSession = sessionObservations.length;
    if (MIN_VOLTAGE_COUNT > 0 && numberOfPointsInSession < MIN_VOLTAGE_COUNT) {
      console.debug('Skipping voltage session', session,
        ' because there are fewer than', MIN_VOLTAGE_COUNT, 'data points');
      continue;
    }
    const firstObservation = sessionObservations[0];
    const firstUtcSeconds = firstObservation.utcSeconds;
    const lastObservation = sessionObservations[sessionObservations.length - 1];
    const lastUtcSeconds = lastObservation.utcSeconds;
    if (MIN_VOLTAGE_TIME_RANGE_SECONDS > 0 && lastUtcSeconds - firstUtcSeconds < MIN_VOLTAGE_TIME_RANGE_SECONDS) {
      console.debug('Skipping voltage session', session,
        ' because first and last are less than', MIN_VOLTAGE_TIME_RANGE_SECONDS, 'seconds apart');
      continue;
    }
    outputObservations = [];
    for (let observationIndex = 0; observationIndex < numberOfPointsInSession; observationIndex++) {
      if (!('batteryVoltage' in sessionObservations[observationIndex])) {
        continue; // Skip if this observation does not have voltage data.
      }
      const batteryVoltage = sessionObservations[observationIndex]['batteryVoltage'];
      if (batteryVoltage < 2) {
        continue; // Ignore data that is too small.
      }
      outputObservations.push(sessionObservations[observationIndex]);
    }
    // Do not include session if no data exists.
    if (outputObservations.length > 0) {
      filteredVoltageObservations[session] = outputObservations;
    }
  }
  return filteredVoltageObservations;
}

function displayVoltageObservations(scatterChart, observations) {
  scatterChart.config.data.datasets = [];
  for (const session of Object.keys(observations)) {
    const sessionObservations = observations[session];
    const numberOfPointsInSession = sessionObservations.length;
    const lineData = [];
    for (let observationIndex = 0; observationIndex < numberOfPointsInSession; observationIndex++) {
      const utcSeconds = sessionObservations[observationIndex].utcSeconds;
      const x = new Date(0);
      x.setUTCSeconds(utcSeconds);
      const y = sessionObservations[observationIndex].batteryVoltage;
      const point = {
        x: x,
        y: y,
      };
      lineData.push(point);
    }
    const color = COLORS[scatterChart.config.data.datasets.length % COLORS.length];
    scatterChart.config.data.datasets.push({
      label: abbreviateSession(session),
      data: lineData,
      borderColor: color,
      backgroundColor: '#00000000',
      lineTension: 0,
    });
  }
  const dateRange = getDateRange(observations);
  const utcSecondsRange = dateRange.maxUtcSeconds - dateRange.minUtcSeconds;
  scatterChart.options.scales.xAxes[0].ticks.stepSize = getGraphStepSize(utcSecondsRange);
  scatterChart.update();
}
