import { createLogger, StringifyObjectsHook } from 'vue-logger-plugin'
import http from '../plugins/auth'
import { vlcApi } from './api.js'


const ServerLogHook = {
    run(event) {
        let logLine = {
            time: new Date().getTime(),
            level: event.level,
            data: event.argumentArray
        }

        let localStorageLogs = JSON.parse(localStorage.getItem("logs"))
        if (!Array.isArray(localStorageLogs)) {
            localStorageLogs = []
        }
        localStorageLogs.push(logLine)
        while (localStorageLogs.length > 250) {
            localStorageLogs.shift()
        }
        localStorage.setItem("logs", JSON.stringify(localStorageLogs))
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
    var logs = JSON.parse(localStorage.getItem("logs"))
    var formData = new FormData();
    formData.append("logs", logs);
    return http.post(vlcApi.sendLogs, logs, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}