import { createApp } from 'vue'
import App from './App.vue'
import './scss/app.scss'
import { API_IP, API_URL } from './config.js'

createApp(App).mount('#app')

const playerWS = new WebSocket("ws://" + API_IP + "/echo", "player");

export const msecToTime = ms => {
    const seconds = Math.floor((ms / 1000) % 60)
    const minutes = Math.floor((ms / (60 * 1000)) % 60)
    const hours = Math.floor((ms / (3600 * 1000)) % 3600)
    return `${hours < 10 ? '0' + hours : hours}:${minutes < 10 ? '0' + minutes : minutes}:${seconds < 10 ? '0' + seconds : seconds
        }`
}
const play = document.getElementById("player_play");
const pause = document.getElementById("player_pause");
const previous = document.getElementById("player_previous");
const next = document.getElementById("player_next");
const shuffle = document.getElementById("player_shuffle");
const repeat = document.getElementById("player_repeat");
const previous10 = document.getElementById("player_previous_10");
const next10 = document.getElementById("player_next_10");

play.addEventListener('click', () => {
    playerWS.send("play");
});
pause.addEventListener('click', () => {
    playerWS.send("pause");
});
previous.addEventListener('click', () => {
    playerWS.send("previous");
});
next.addEventListener('click', () => {
    playerWS.send("next");
});
shuffle.addEventListener('click', () => {
    playerWS.send("shuffle");
});
repeat.addEventListener('click', () => {
    playerWS.send("repeat");
});
previous10.addEventListener('click', () => {
    playerWS.send("previous10");
});
next10.addEventListener('click', () => {
    playerWS.send("next10");
});

var lastLoadedMediaUri = ""

playerWS.onmessage = (event) => {
    const title = document.getElementById("title");
    const artist = document.getElementById("artist");
    const time = document.getElementById("time");
    const duration = document.getElementById("duration");
    const artwork = document.getElementById("player_artwork");
    const msg = JSON.parse(event.data);
    title.textContent = msg.title
    artist.textContent = msg.artist
    time.textContent = msecToTime(new Date(msg.progress))
    duration.textContent = msecToTime(new Date(msg.duration))
    if (lastLoadedMediaUri != msg.uri) {
        artwork.src = API_URL + "/artwork?randomizer=" + Date.now()
        lastLoadedMediaUri = msg.uri
    }

    if (msg.playing) {
        play.style.display = "none";
        pause.style.display = "inline-block";
    } else {
        play.style.display = "inline-block";
        pause.style.display = "none";
    }

}
