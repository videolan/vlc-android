const API_PORT = "8080"
export const API_IP = process.env.NODE_ENV === 'development' ? `${process.env.VUE_APP_DEVICE_IP}:${API_PORT}` : `${location.host}`
export const API_URL = `http://${API_IP}/`;

export const API_CONFIG = {
    LIST_LOGFILES: `${API_URL}list-logfiles`,
    DOWNLOAD_LOGFILE: `${API_URL}download-logfile?file=`,
}
