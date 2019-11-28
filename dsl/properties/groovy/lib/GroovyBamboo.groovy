import com.cloudbees.flowpdf.client.HTTPRequest
import com.cloudbees.flowpdf.client.REST
import com.cloudbees.flowpdf.exceptions.UnexpectedEmptyValue
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import com.cloudbees.flowpdf.components.ComponentManager

import com.cloudbees.flowpdf.*

/**
* GroovyBamboo
*/
class GroovyBamboo extends FlowPlugin {

    @Override
    Map<String, Object> pluginInfo() {
        return [
                pluginName     : '@PLUGIN_KEY@',
                pluginVersion  : '@PLUGIN_VERSION@',
                configFields   : ['config'],
                configLocations: ['ec_plugin_cfgs'],
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
/**
    * Procedure parameters:
    * @param config
    * @param projectKey
    * @param planKey
    * @param initialRecordsCount
    * @param previewMode
    * @param transformScript
    * @param debug
    * @param releaseName
    * @param releaseProjectName
    
    */
    def collectReportingData(StepParameters paramsStep, StepResult sr) {
        def params = paramsStep.getAsMap()
        def requestKey = "${params['projectKey']}" + (params['planKey'] ? "-${params['planKey']}" : "")

        if (params['debug']) {
            log.setLogLevel(log.LOG_DEBUG)
        }
        
        ReportingGroovyBamboo reporting = (ReportingGroovyBamboo) ComponentManager.loadComponent(ReportingGroovyBamboo.class, [
                reportObjectTypes  : ['build'],
                initialRecordsCount: params['initialRecordsCount'],
                metadataUniqueKey  : requestKey,
                payloadKeys        : ['startTime'],
        ], this)

        reporting.collectReportingData()
    }
// === step ends ===

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