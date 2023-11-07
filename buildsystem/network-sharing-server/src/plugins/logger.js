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
    }
}


// create logger with options
const logger = createLogger({
    enabled: true,
    level: 'debug',
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