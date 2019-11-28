import com.cloudbees.flowpdf.*
import com.cloudbees.flowpdf.components.reporting.Dataset
import com.cloudbees.flowpdf.components.reporting.Metadata
import com.cloudbees.flowpdf.components.reporting.Reporting

/**
 * User implementation of the reporting classes
 */
class ReportingGroovyBamboo extends Reporting {

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