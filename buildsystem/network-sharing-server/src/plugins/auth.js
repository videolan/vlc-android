import axios from 'axios';
import router from '../main'

const http = axios.create();

/* Response Interceptors */
const interceptResErrors = (err) => {
    try {
        if (err.response.status == 401) {
            router.push({ name: 'LoginPage' })
            return
        }
        // check for response code 123 and redirect to login
        err = Object.assign(new Error(), { message: err.response.data });
    } catch (e) {
        // check for response code 123 and redirect to login
        // Will return err if something goes wrong
    }
    return Promise.reject(err);
};
const interceptResponse = (res) => {
    try {
        // check for response code 123 and redirect to login
        return Promise.resolve(res.data);
    } catch (e) {
        // check for response code 123 and redirect to login
        return Promise.resolve(res);
    }
};
http.interceptors.response.use(interceptResponse, interceptResErrors);

/* Request Interceptors */
const interceptReqErrors = err => Promise.reject(err);
const interceptRequest = (config) => {
    return config;
};
http.interceptors.request.use(interceptRequest, interceptReqErrors);

export default http;