import axios from 'axios';
import router from '../main'

const http = axios.create();

/* Response Interceptors */
const interceptResErrors = (err) => {
    const currentRouteName = router.currentRoute.value.name
    const isLogin = currentRouteName == 'LoginPage' || currentRouteName == 'LoginPageError'
    try {
        // If the request is unauthorized, fall back to login page
        if (err.response.status == 401) {
            if (!isLogin) {
                router.push({ name: 'LoginPage' })
                return
            }
        }
    } catch (e) {
        if (err.response.status == 401) {
            if (!isLogin) {
                router.push({ name: 'LoginPage' })
            }
        }
    }
    return Promise.reject(err);
};
const interceptResponse = (res) => {
    try {
        return Promise.resolve(res);
    } catch (e) {
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