import com.cloudbees.flowpdf.Context
import com.cloudbees.flowpdf.FlowPlugin
import com.cloudbees.flowpdf.StepParameters
import com.cloudbees.flowpdf.StepResult
import com.cloudbees.flowpdf.client.HTTPRequest
import com.cloudbees.flowpdf.client.REST
import com.cloudbees.flowpdf.components.ComponentManager
import com.cloudbees.flowpdf.components.reporting.Dataset
import com.cloudbees.flowpdf.components.reporting.Metadata
import com.cloudbees.flowpdf.components.reporting.Reporting
import com.cloudbees.flowpdf.exceptions.UnexpectedEmptyValue

/**
 * CBBambooGroovy
 */
class CBBambooGroovy extends FlowPlugin {

    @Override
    Map<String, Object> pluginInfo() {
        return [
                pluginName         : '@PLUGIN_KEY@',
                pluginVersion      : '@PLUGIN_VERSION@',
                configFields       : ['config'],
                configLocations    : ['ec_plugin_cfgs'],
                defaultConfigValues: [:]
        ]
    }

/**
 * sampleRESTProcedure - Sample REST Procedure/Sample REST Procedure
 * Add your code into this method and it will be called when the step runs

 * @param config (required: true)

 * @param username (required: true)

 */
    def sampleRESTProcedure(StepParameters runtimeParameters, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
                "sampleRESTProcedure was invoked with StepParameters",
                /* runtimeParameters contains both configuration and procedure parameters */
                runtimeParameters.toString()
        )

        Context context = getContext()

        // Setting job step summary to the config name
        sr.setJobStepSummary(runtimeParameters.getParameter('config').getValue() ?: 'null')

        sr.setReportUrl("Sample Report", 'https://cloudbees.com')
        sr.apply()
        log.info("step Sample REST Procedure has been finished")
    }
// === step ends ===

    def collectReportingData(StepParameters paramsStep, StepResult sr) {
        def params = paramsStep.getAsMap()
        def requestKey = "${params['projectKey']}" + (params['planKey'] ? "-${params['planKey']}" : "")

        if (params['debug']) {
            log.setLogLevel(log.LOG_DEBUG)
        }

        BambooReporting reporting = (BambooReporting) ComponentManager.loadComponent(BambooReporting.class, [
                reportObjectTypes  : ['build'],
                initialRecordsCount: params['initialRecordsCount'],
                metadataUniqueKey  : requestKey,
                payloadKeys        : ['startTime'],
        ], this)

        reporting.collectReportingData()
    }

    def validateCRDParams(StepParameters params, def sr) {
        def required = ['config', 'projectKey']
        for (String param : required) {
            if (!params.isParameterHasValue(param)) {
                throw new UnexpectedEmptyValue(param, 'string')
            }
        }

        sr.setJobStepSummary("success")
        sr.setJobStepOutcome("Parameters check passed")
    }

/** User defined collect reporting data related methods stat*/
    def compareISODateTimes(String date1, String date2) {
        def longDate1 = date1.replaceAll(/[^0-9]/, '').toLong()
        def longDate2 = date2.replaceAll(/[^0-9]/, '').toLong()
        return longDate1.compareTo(longDate2)
    }

    def getBuildRunsAfter(def projectKey, def planKey, def parameters) {
        def afterTime = parameters['afterTime']

        def results = []

        def requestKey = "${projectKey}" + (planKey ? "-$planKey" : "")
        def requestPath = "/rest/api/latest/result/${requestKey}.json"
        def requestPackSize = 25

        def requestParams = [
                "expand"     : "results.result.labels",
                "max-results": requestPackSize,
                "start-index": 0
        ]

        def reachedGivenTime = false
        def haveMoreResults = true

        Context context = getContext()
        REST rest = context.newRESTClient()
        HTTPRequest request = rest.newRequest(
                method: 'GET',
                path: requestPath,
                contentType: 'JSON',
                query: requestParams
        )

        while (!reachedGivenTime && haveMoreResults) {
            def buildResults = rest.doRequest(request)
            haveMoreResults = buildResults['results']['size'] >= requestPackSize

//           for (def buildResult in buildResults){
            for (def buildResult in buildResults['results']['result']) {
                def parsed = buildResult
                if (compareISODateTimes(afterTime, parsed['buildStartedTime']) >= 0) {
                    reachedGivenTime = true
                    break
                }
                results += parsed
            }
            requestParams["start-index"] += requestPackSize
        }
        log.info("getBuildRunsAfter has been executed, result: ${results}")
        return results
    }

    def getBuildRuns(def projectKey, def planKey, def parameters) {
        def requestKey = "${projectKey}" + (planKey ? "-$planKey" : "")
        def requestPath = "/rest/api/latest/result/${requestKey}.json"
        def limit = parameters['maxResult'] ?: 0

        Context context = getContext()
        REST rest = context.newRESTClient()
        HTTPRequest request = rest.newRequest(
                method: 'GET',
                path: requestPath,
                contentType: 'JSON',
                query: [
                        'expand'     : 'results.result',
                        "max-results": limit
                ]
        )

        def buildResult = rest.doRequest(request)
        log.info("getBuildRuns has been executed, result: ${buildResult['results']['result']}")
        return buildResult['results']['result']

    }
    /** User defined collect reporting data related methods end*/

}

/**
 * User implementation of the reporting classes
 */
class BambooReporting extends Reporting {

    @Override
    int compareMetadata(Metadata param1, Metadata param2) {
        def value1 = param1.getValue()
        def value2 = param2.getValue()

        def pluginObject = this.getPluginObject()
        return pluginObject.compareISODateTimes(value1['startTime'], value2['startTime'])
    }


    @Override
    List<Map<String, Object>> initialGetRecords(FlowPlugin flowPlugin, int i = 10) {
        def params = flowPlugin.getContext().getRuntimeParameters().getAsMap()
        def records = (flowPlugin as CBBambooGroovy).getBuildRuns(params['projectKey'], params['planKey'], [
                maxResults: i
        ])
        return records
    }

    @Override
    List<Map<String, Object>> getRecordsAfter(FlowPlugin flowPlugin, Metadata metadata) {
        def params = flowPlugin.getContext().getRuntimeParameters().getAsMap()
        def metadataValues = metadata.getValue()

        def log = flowPlugin.getLog()
        log.info("Got metadata value in getRecordsAfter:  ${metadataValues.toString()}")

        def records = flowPlugin.getBuildRunsAfter(params['projectKey'], params['planKey'], [
                maxResults: 0,
                afterTime : metadataValues['startTime']
        ])

        log.info("Records after GetRecordsAfter ${records.toString()}")
        return records
    }

    @Override
    Map<String, Object> getLastRecord(FlowPlugin flowPlugin) {
        def params = flowPlugin.getContext().getRuntimeParameters().getAsMap()
        def log = flowPlugin.getLog()
        log.info("Last record runtime params: ${params.toString()}")
        List<Map<String, Object>> runs = flowPlugin.getBuildRuns(params['projectKey'], params['planKey'], [maxResults: 1])
        return runs[0]
    }

    @Override
    Dataset buildDataset(FlowPlugin plugin, List<Map> records) {
        def dataset = this.newDataset(['build'], [])
        def context = plugin.getContext()
        def params = context.getRuntimeParameters().getAsMap()

        def log = plugin.getLog()
        log.info("Start procedure buildDataset")

        log.info("buildDataset received params: ${params}")

        def buildStateMapping = [
                'Successful': 'SUCCESS',
                'Failed'    : 'FAILURE',
                'Unknown'   : 'NOT_BUILT',
        ]

        for (def row in records.reverse()) {
            def payload = [
                    source             : 'Bamboo',
                    pluginName         : '@PLUGIN_NAME@',
                    projectName        : context.retrieveCurrentProjectName(),
                    releaseName        : params['releaseName'] ?: '',

                    releaseUri         : (params['projectKey'] + (params['planKey'] ? "-${params['planKey']}" : '')),
                    releaseProjectName : params['releaseProjectName'] ?: '',
                    pluginConfiguration: params['config'],
                    baseDrilldownUrl   : (params['baseDrilldownUrl'] ?: params['endpoint']) + '/browse/',
                    buildNumber        : row['buildNumber'],
                    timestamp          : row['buildStartedTime'],
                    endTime            : row['buildCompletedTime'],
                    startTime          : row['buildStartedTime'],
                    buildStatus        : buildStateMapping[row['buildState'] ?: 'Unknown'],
                    launchedBy         : 'N/A',
                    jobName            : row['key'],
                    duration           : row['buildDuration'],
                    // tags                : '',
                    sourceUrl          : row['link']['href'],
            ]

            for (key in payload.keySet()) {
                if (!payload[key]) {
                    log.info("Payload parameter '${key}' don't have a value and will not be sent.")
                    payload.remove(key)
                }
            }
            log.info("procedure buildDataset created payload: ${payload}")
            dataset.newData(
                    reportObjectType: 'build',
                    values: payload
            )
        }

        log.info("Dataset: ${dataset.data}")
        return dataset
    }
}