/* global window, $, JSONEditor, alert, gapi, moment, Chart */

/* TODO: break this down into modules and templates and create build system
** this may require a separate repository to avoid npm dependencies
** in this one - the advantage would be a more robust SPA with templating
** that may be portable to other services
 */

function govDate (date) {
  if (date === 'today') {
    return moment().format('D MMMM YYYY')
  }
  return moment(date).format('D MMMM YYYY')
}

function extractDate (dateTime) {
  return dateTime.substr(0, 8)
}

function govDateTime (date) {
  //20181106 1648
  if (date === 'today') {
    return moment().format('D MMMM YYYY')
  }
  const withoutTime = extractDate(date);
  return moment(withoutTime).format('D MMMM YYYY') + ' ' + date.substr(8, 2) + ':' + date.substr(10, 2)
}

const GAViews = {
  dev: {
    name: 'Development',
    id: '155063315'
  },
  staging: {
    name: 'Staging',
    id: '155056605'
  },
  qa: {
    name: 'QA',
    id: '155063519'
  },
  production: {
    name: 'Production',
    id: '155026244'
  }
};

let GAConfig = {};

const GADefaultConfig = {
  view: GAViews['production'],
  startPeriod: '30daysAgo',
  endPeriod: 'today',
  slug: '/',
  sectionSlug: '',
  query: 'pageViewQuery',
  samplingLevel: 'LARGE'
};

function handleLoadGA () {
  setSectionName('');
  if (GAConfig.chart) {
    GAConfig.chart.destroy()
  }
  return loadGAQuery()
}

function handleSectionStatsLink (e) {
  e.preventDefault();
  const $config = $(e.target);
  let sectionName = 'Page: ' + $config.attr('data-section-slug').replace(/-/gi, ' ');
  if (GAConfig.slug !== $config.attr('data-slug')) {
    sectionName = $config.attr('data-slug') + '/' + $config.attr('data-section-slug')
  }
  setSectionName(sectionName);
  return loadGAQuery(setConfig({
    query: $config.attr('data-query'),
    sectionSlug: $config.attr('data-section-slug')
  }))
}

function setSectionName (name) {
  $('#section-name').text(name)
}

function setConfig (custom) {
  custom = custom || {};
  const $viewConfig = $('#ga-view-select');
  const $slug = $('#form-selector');
  const userConfig = {
    startPeriod: $('#ga-period').val(),
    slug: $slug.val(),
    view: GAViews[$viewConfig.val()],
    query: $viewConfig.attr('data-query'),
    formNames: GAConfig.formNames
  };
  return Object.assign({}, GADefaultConfig, userConfig, custom)
}

function loadGAQuery (config) {
  loadingTotals();
  GAConfig = config || setConfig();
  window[GAConfig.query](GAConfig)
}

function setSessionsTotal (total) {
  $('#sessions-total').html(total)
}

function setSubmissionsTotal (total) {
  $('#submissions-total').html(total);
  setCompletionRate(calcCompletionRate() + '%')
}

function setLegacySubmissionsTotal (total) {
  $('#legacy-submissions-total').html(total);
}

function calcCompletionRate () {
  const sessions = parseInt($('#sessions-total').text());
  const submissions = parseInt($('#submissions-total').text());
  return sessions > 0 && submissions > 0 ? ((submissions / sessions) * 100).toFixed(2) : 0
}

function setCompletionRate (rate) {
  $('#completion-rate').html(rate)
}

function setPageViewsTotal (total) {
  $('#views-total').html(total)
}

function setErrorsTotal (total) {
  $('#errors-total').html(total)
}

function setFieldErrorsTotal (total) {
  $('#fieldErrorsTotal').html(total)
}

function emptyTables () {
  $('.stats-table').hide().find('.stats-results').empty()
}

function loadingTotals () {
  $('.stats-container').show();
  $('.field-errors').hide();
  emptyTables();
  const spinnerHtml = '<div class="lds-ring"><div></div><div></div><div></div><div></div></div>';
  setLegacySubmissionsTotal(0);
  setSubmissionsTotal(spinnerHtml);
  setPageViewsTotal(spinnerHtml);
  setErrorsTotal(spinnerHtml);
  setSessionsTotal(spinnerHtml);
  setCompletionRate(spinnerHtml);
}

function showStatsTable (stats) {
  $('.stats-table').hide();
  $('.' + stats).show()
}

function GAQuery (reportRequests, parseFn) {
  gapi.client.request({
    path: '/v4/reports:batchGet',
    root: 'https://analyticsreporting.googleapis.com/',
    method: 'POST',
    body: {
      reportRequests: reportRequests
    }
  }).then(parseFn, console.error.bind(console))
}

/* for when pathKeys are used
function pathFilters (config) {
  return config.pathKeys.reduce((list, key) => {
    list.push(pathLevelFilter(key.slug, key.level))
    return list
  })
}
*/

function viewFilters (config) {
  let filters = [withSlug(config.slug)];
  if (config.sectionSlug) {
    filters.push(pathLevelFilter(config.sectionSlug, 4))
  }
  return filters
}

function filterConcat (config, filters) {
  return filters.reduce((list, filter) => {
    list.push(...filter(config));
    return list
  }, [])
}

function pathLevelFilter (slug, level) {
  return {
    dimensionName: 'ga:pagePathLevel' + level,
    operator: 'PARTIAL',
    expressions: [slug]
  }
}

function pageTitleFilter (title) {
  return {
    dimensionName: 'ga:pageTitle',
    operator: 'PARTIAL',
    expressions: [title]
  }
}

function submissionFilter () {
  return [{
    dimensionName: 'ga:eventCategory',
    operator: 'EXACT',
    expressions: ['submission']
  }]
}

function errorFilter () {
  return [{
    dimensionName: 'ga:eventCategory',
    operator: 'PARTIAL',
    expressions: ['error']
  }]
}

function fieldErrorFilter (field, pageTitle) {
  function filter() {
    return [{
      dimensionName: 'ga:eventAction',
      operator: 'EXACT',
      expressions: [field]
    }, pageTitleFilter(pageTitle)]
  }
  return filter
}

function fieldErrorQuery (fieldFilter) {
  const config = setConfig();
  return [
    {
      viewId: config.view.id,
      samplingLevel:  config.samplingLevel,
      dateRanges: [
        {
          startDate: config.startPeriod,
          endDate: 'today'
        }
      ],
      metrics: [
        {expression: 'ga:totalEvents'}
      ],
      dimensionFilterClauses: [{
        operator: 'AND',
        filters: filterConcat(config, [fieldFilter])
      }],
      dimensions: [
        {name: 'ga:eventLabel'},
        {name: 'ga:dateHourMinute'},
        {name: 'ga:browser'},
        {name: 'ga:operatingSystem'}
      ],
      orderBys: [
        {
          fieldName: 'ga:totalEvents',
          sortOrder: 'DESCENDING'
        }
      ]
    }
  ]
}

function getPageNameForError (pageTitle) {
  // split page title on - and ignore the first 'Error:' part
  return pageTitle.split('-')[0].substr(6)
}

function getFormName (pageTitle) {
  return pageTitle.split('-')[1].trim();
}

function handleDrillDownError (e) {
  e.preventDefault();
  loadingTotals();
  emptyTables();
  hideChart();
  $('.stats-container').hide();
  $('.field-errors').show();
  showStatsTable('field-errors-table');
  const $elData = $(e.currentTarget).data();
  $('#field-error-field').text(getFormName($elData.title) + ' / ' + getPageNameForError($elData.title) + ' / ' + $elData.field);
  const fieldFilter = fieldErrorFilter($elData.field, $elData.title);
  GAQuery(fieldErrorQuery(fieldFilter), parseErrorDrillDown)
}

function parseErrorDrillDown (response) {
  const reports = response.result.reports;
  parseFieldErrorEvents(reports[0].data)
}

function withSlug (slug) {
  return !slug || slug === '/' ? pathLevelFilter('submissions', 1) : pathLevelFilter(slug, 3)
}

function query (config, names) {
  const core = {
    viewId: config.view.id,
    samplingLevel:  config.samplingLevel,
    dateRanges: [
      {
        startDate: config.startPeriod,
        endDate: config.endPeriod
      }
    ]
  };
  const queries = {
    submissions: {
      metrics: [
        {expression: 'ga:totalEvents'}
      ],
      dimensionFilterClauses: [{
        operator: 'AND',
        filters: filterConcat(config, [viewFilters, submissionFilter])
      }],
      dimensions: [
        {name: 'ga:pageTitle'},
        {name: 'ga:eventAction'},
        {name: 'ga:eventLabel'},
        {name: 'ga:date'}
      ]
    },
    acknowledgementPageViews: {
      metrics: [
        {expression: 'ga:uniquePageviews'}
      ],
      dimensionFilterClauses: [{
        operator: 'AND',
        filters: [
          withSlug(config.slug),
          pathLevelFilter('acknowledgement', 2)
        ]
      }],
      dimensions: [
        {name: 'ga:pagePath'},
        {name: 'ga:browser'},
        {name: 'ga:date'}
      ],
      orderBys: [
        {
          fieldName: 'ga:uniquePageViews',
          sortOrder: 'DESCENDING'
        }
      ]
    },
    pageViews: {
      metrics: [
        {expression: 'ga:pageviews'},
        {expression: 'ga:sessions'}
      ],
      dimensionFilterClauses: [{
        filters: viewFilters(config)
      }],
      dimensions: [
        {name: 'ga:pagePath'},
        {name: 'ga:date'}
      ],
      orderBys: [
        {
          fieldName: 'ga:pageviews',
          sortOrder: 'DESCENDING'
        }
      ]
    },
    sectionView: {
        metrics: [
          {expression: 'ga:pageviews'},
          {expression: 'ga:sessions'}
        ],
          dimensionFilterClauses: [{
        operator: 'AND',
        filters: viewFilters(config)
      }],
        dimensions: [
        {name: 'ga:pagePath'},
        {name: 'ga:date'},
        {name: 'ga:pageTitle'}
      ],
        orderBys: [
        {
          fieldName: 'ga:pageviews',
          sortOrder: 'DESCENDING'
        }
      ]
    },
    userError: {
      metrics: [
        {expression: 'ga:totalEvents'}
      ],
      dimensionFilterClauses: [{
        operator: 'AND',
        filters: filterConcat(config, [viewFilters, errorFilter])
      }],
      dimensions: [
        {name: 'ga:pageTitle'},
        {name: 'ga:eventLabel'},
        {name: 'ga:eventAction'},
        {name: 'ga:date'}
      ],
      orderBys: [
        {
          fieldName: 'ga:totalEvents',
          sortOrder: 'DESCENDING'
        }
      ]
    },
    allForms: {
      metrics: [
        {expression: 'ga:pageviews'}
      ],
      dimensions: [
        {name: 'ga:pagePathLevel3'}
      ]
    }
  };

  return names.reduce((list, name) => {
    list.push(Object.assign({}, core, queries[name]));
    return list
  }, [])
}

function sectionViewQuery (config) {
  GAQuery([query(config, ['sectionView', 'userError'])], parseSectionResults)
}

function pageViewQuery (config) {
  const names = ['pageViews', 'submissions', 'userError', 'acknowledgementPageViews'];

  if (!GAConfig.formNames) {
    names.push('allForms')
  }

  GAQuery(query(config, names), parseResults)
}

const tdClass = "govuk-table__cell";
const trClass = "govuk-table__row";
const fontSize16 = "govuk-!-font-size-16";

function writePageViewRow (row) {
  const pageLevels = row.dimensions[0].split('/');
  const tr = $('<tr class="' + trClass + '"></tr>');
  const td1 = $('<td class="' + tdClass + ' ' + fontSize16 + '"></td>');
  td1.append('<a href="#" class="section-stats" data-query="sectionViewQuery" data-view="dev" data-slug="' + pageLevels[3] + '" data-section-slug="' + pageLevels[4] + '">' + row.dimensions[0] + '</a><br>' + govDate(row.dimensions[1]));
  const td2 = $('<td class="' + tdClass + '">' + row.metrics[0].values[0] + '</td>');
  if (row.dimensions[2]) {
    td1.find('a').text(row.dimensions[2].split('-')[0].trim())
  }
  tr.append(td1).append(td2);
  $('#views-table').append(tr)
}

function writeSubmissionRow (row) {
  const tr = $('<tr class="' + trClass + '"></tr>');
  const td1 = $('<td class="' + tdClass + ' ' + fontSize16 + '">' + row.dimensions[2] + '</td>');
  const td2 = $('<td class="' + tdClass + ' ' + fontSize16 + '">' + row.dimensions[1] + '<br>' + govDate(row.dimensions[3]) + '</td>');
  const td3 = $('<td class="' + tdClass + '">' + row.metrics[0].values[0] + '</td>');
  tr.append(td1).append(td2).append(td3);
  $('#submissions-table').append(tr)
}

function writeErrorRow (row) {
  const tr = $('<tr class="' + trClass + '"></tr>');
  const td1 = $('<td class="' + tdClass + ' ' + fontSize16 + '">' + row.dimensions[2] + '</td>');
  const td2 = $('<td class="' + tdClass + ' ' + fontSize16 + '"><span class="hidden-dimensions">Date: ' + govDate(row.dimensions[3]) + '<br>' + row.dimensions[0] + '</span></td>');
  const td3 = $('<td class="' + tdClass + '">' + row.metrics[0].values[0] + '</td>');
  const $errorLink = $('<a href="#" class="drilldown-error govuk-error-message" data-title="' + row.dimensions[0] + '" data-field="' + row.dimensions[2] + '"><span class="govuk-error-message  govuk-!-font-size-16">' + row.dimensions[1] + '</span></a>');
  td2.prepend($errorLink);
  tr.append(td1).append(td2).append(td3);
  $('#errors-table').append(tr)
}

function writeFieldErrorRow (row) {
  const tr = $('<tr class="' + trClass + '"></tr>');
  const td1 = $('<td class="' + tdClass + ' ' + fontSize16 + '">' + govDateTime(row.dimensions[1]) + '</td>');
  const td2 = $('<td class="' + tdClass + ' ' + fontSize16 + '"><span class="hidden-dimensions">Browser: ' + row.dimensions[2] + ', OS: ' + row.dimensions[3] + '</span></td>');
  const td3 = $('<td class="' + tdClass + '">' + row.metrics[0].values[0] + '</td>');
  const $errorLink = $('<span class="govuk-error-message  ' + fontSize16 + '">' + row.dimensions[0] + '</span>');
  td2.prepend($errorLink);
  tr.append(td1).append(td2).append(td3);
  $('#field-errors-table').append(tr)
}

function cloneTimeline () {
  return Object.assign({}, GAConfig.timeline)
}

function buildPageViewTimeline (resolution, data, fn) {
  resolution = resolution || 'days';
  const periodCount = GAConfig.startPeriod.split('daysAgo')[0];
  const timeline = {};
  const max = moment();
  let plot = max.subtract(parseInt(periodCount) + 1, 'days');
  let i = 0;
  for (i; i < parseInt(periodCount) + 1; i++) {
    plot = plot.add(1, resolution);
    timeline[plot.format('YYYYMMDD')] = 0;
    if (i === parseInt(periodCount)) {
      GAConfig.timeline = Object.assign({}, timeline);
      fn(data, cloneTimeline())
    }
  }
}

function parseResults (response) {
  const reports = response.result.reports;
  parsePageViews(reports[0].data);
  parseSubmissions(reports[1].data);
  parseErrorEvents(reports[2].data);
  parseLegacySubmissions(reports[3].data);
  if (reports[4]) {
    GAConfig.formNames = parseFormNameResults(reports[4].data);
    populateFormSelector()
  }
}

function parsePageViews (data) {
  buildPageViewTimeline('days', data, parsePageRows)
}

function parseSectionResults (response) {
  const reports = response.result.reports;
  parsePageViews(reports[0].data);
  parseErrorEvents(reports[1].data);
  setSubmissionsTotal('N/A')
}

function buildChartY (timeline) {
  return Object.keys(timeline).reduce((data, key) => {
    data.push(timeline[key]);
    return data
  }, [])
}

function addChartData (chart, data) {
  chart.data.datasets.push(data);
  chart.update()
}

function createChart (timeline) {
  if (GAConfig.chart) {
    GAConfig.chart.destroy()
  }
  const chartVals = buildChartY(timeline);
  const ctx = document.getElementById('pageViewsChart');

  GAConfig.chart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: Object.keys(timeline),
      datasets: [{
        label: '# of page views',
        data: chartVals,
        borderWidth: 1
      }]
    },
    options: {
      scales: {
        xAxes: [{
          type: 'time',
          time: {
            unit: 'day'
          }
        }]
      }
    }
  })
}

function showChart () {
  $('.chart-container').show()
}

function hideChart () {
  $('.chart-container').hide()
}

function parsePageRows (data, timeline) {
  const sessionsTimeline = cloneTimeline();
  if (data.rows) {
    showStatsTable('views-table');
    data.rows.forEach(row => {
      writePageViewRow(row);
      timeline[row.dimensions[1]] += parseInt(row.metrics[0].values[0]);
      sessionsTimeline[row.dimensions[1]] += parseInt(row.metrics[0].values[1])
    })
  }
  createChart(timeline);
  const sessionChartData = {
    label: '# of sessions',
    data: buildChartY(sessionsTimeline),
    color: '#005ea5',
    zeroLineWidth: 0,
    pointBackgroundColor: '#005ea5',
    backgroundColor: 'rgba(0, 94, 165, 0.4)',
    borderWidth: 1
  };
  addChartData(GAConfig.chart, sessionChartData);
  showChart();
  setSessionsTotal(data.totals[0].values[1]);
  setPageViewsTotal(data.totals[0].values[0])
}

function parseSubmissions (data) {
  const timeline = cloneTimeline();
  if (data.rows) {
    data.rows.forEach(row => {
      writeSubmissionRow(row);
      timeline[row.dimensions[3]] += parseInt(row.metrics[0].values[0])
    });
    const submissionChartData = {
      label: '# of submissions',
      data: buildChartY(timeline),
      color: '#00823b',
      zeroLineWidth: 0,
      pointBackgroundColor: '#00823b',
      backgroundColor: 'rgba(0, 130, 59, 0.8)',
      borderWidth: 1
    };
    addChartData(GAConfig.chart, submissionChartData)
  }
  setSubmissionsTotal(data.rowCount || 0)
}

function parseLegacySubmissions (data) {
  setLegacySubmissionsTotal(data.totals[0].values[0])
}

function parseFieldErrorEvents (errorData) {
  if (errorData.rows) {
    errorData.rows.forEach(row => {
      writeFieldErrorRow(row)
    })
  }
  setFieldErrorsTotal(errorData.totals[0].values[0])
}

function parseErrorEvents (errorData) {
  const timeline = cloneTimeline();
  if (errorData.rows) {
    errorData.rows.forEach(row => {
      timeline[row.dimensions[3]] += parseInt(row.metrics[0].values[0]);
      writeErrorRow(row)
    });
    const errorChartData = {
      label: '# of errors',
      data: buildChartY(timeline),
      color: '#b10e1e',
      zeroLineWidth: 0,
      pointBackgroundColor: '#b10e1e',
      backgroundColor: 'rgba(177, 14, 30, 0.8)',
      borderWidth: 1
    };
    addChartData(GAConfig.chart, errorChartData)
  }
  setErrorsTotal(errorData.totals[0].values[0])
}

function handleStatsLink (e) {
  e.preventDefault();
  const $link = $(e.currentTarget);
  const stats = $link.attr('data-stats');
  showStatsTable(stats)
}

function populateFormSelector () {
  GAConfig.formNames.forEach(name => {
      $('#form-selector').append('<option value="' + name + '">' + name + '</option>')
  })
}

function parseFormNameResults (data) {
  return data.rows.reduce((names, row) => {
    const name = row.dimensions[0].replace(/\//gi, '').trim();
    if (!names.includes(name) && !/^[A-F0-9]{8,9}-/.test(name) && !/^testGroupId/.test(name)) {
      if (name.length) {
        names.push(name)
      }
    }
    return names
  }, [])
}

$(document).ready(function () {

  $('#form-analytics')
    .on('click', 'button.load-ga', handleLoadGA)
    .on('click', 'a.stats-link', handleStatsLink)
    .on('click', 'a.section-stats', handleSectionStatsLink)
    .on('click', 'a.drilldown-error', handleDrillDownError)
});
