import { createLogger, StringifyObjectsHook } from 'vue-logger-plugin'
import http from '../plugins/auth'
import { vlcApi } from './api.js'

const logs = []

const ServerLogHook = {
    run(event) {
        logs.push({
            time: new Date().getTime(),
            level: event.level,
            data: event.argumentArray
        })
        if (logs.length > 200) logs.shift()
    }
}
let logLevel = 'info'
if (process.env.NODE_ENV === 'development') { logLevel = 'debug' }
// create logger with options
const logger = createLogger({
    enabled: true,
    level: logLevel,
    callerInfo: true,
    consoleEnabled: process.env.NODE_ENV === 'development',
    beforeHooks: [StringifyObjectsHook],
    afterHooks: [ServerLogHook]
})
export default logger

export function sendLogs() {
    var formData = new FormData();
    formData.append("logs", logs);
    http.post(vlcApi.sendLogs, logs, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}