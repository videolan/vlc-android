import {MDCRipple} from '@material/ripple';
import {MDCTopAppBar} from '@material/top-app-bar';
import {MDCDataTable} from '@material/data-table';


import icon from "../asset/resource/icon.png"
import android_chrome_192 from "../asset/resource/android-chrome-192x192.png"
import android_chrome_512 from "../asset/resource/android-chrome-512x512.png"
import favicon from "../asset/resource/favicon.ico"
import favicon_16 from "../asset/resource/favicon-16x16.png"
import favicon_32 from "../asset/resource/favicon-32x32.png"
import index from "../html/index.html"


//const playerWS = new WebSocket("wss://"+window.location.origin+"/echo", "protocolOne");
const playerWS = new WebSocket("ws://192.168.1.83:8080/echo", "player");
playerWS.onopen = (event) => {
};


export const msecToTime = ms => {
  const seconds = Math.floor((ms / 1000) % 60)
  const minutes = Math.floor((ms / (60 * 1000)) % 60)
  const hours = Math.floor((ms / (3600 * 1000)) % 3600)
  return `${hours < 10 ? '0' + hours : hours}:${minutes < 10 ? '0' + minutes : minutes}:${
    seconds < 10 ? '0' + seconds : seconds
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

play.addEventListener('click', (event) => {
  playerWS.send("play");
});
pause.addEventListener('click', (event) => {
  playerWS.send("pause");
});
previous.addEventListener('click', (event) => {
  playerWS.send("previous");
});
next.addEventListener('click', (event) => {
  playerWS.send("next");
});
shuffle.addEventListener('click', (event) => {
  playerWS.send("shuffle");
});
repeat.addEventListener('click', (event) => {
  playerWS.send("repeat");
});
previous10.addEventListener('click', (event) => {
  playerWS.send("previous10");
});
next10.addEventListener('click', (event) => {
  playerWS.send("next10");
});

var lastLoadedMediaUri = ""

playerWS.onmessage = (event) => {
  console.log(event.data);
  const player = document.getElementById("player");
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
    artwork.src = "http://192.168.1.83:8080/artwork?randomizer="+Date.now()
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