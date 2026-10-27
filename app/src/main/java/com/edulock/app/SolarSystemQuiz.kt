package com.edulock.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SolarSystemQuizContent() {
    SolarSystemWebView(quizMode = true, height = 600.dp)
}

@Composable
fun SolarSystemModelPreview() {
    SolarSystemWebView(quizMode = false, height = 460.dp)
}

@Composable
private fun SolarSystemWebView(quizMode: Boolean, height: Dp) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(height),
        factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.rgb(4, 8, 30))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                webViewClient = WebViewClient()
                tag = quizMode
                loadDataWithBaseURL("https://edulock.local/", solarSystemHtml(quizMode), "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != quizMode) {
                webView.tag = quizMode
                webView.loadDataWithBaseURL("https://edulock.local/", solarSystemHtml(quizMode), "text/html", "UTF-8", null)
            }
        }
    )
}

private fun solarSystemHtml(quizMode: Boolean): String = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{box-sizing:border-box}html,body{margin:0;padding:0;background:#04081e;color:#f4f7ff;font-family:system-ui,-apple-system,sans-serif}
body{background:radial-gradient(ellipse at 50% 45%,#17214c 0%,#090e2c 48%,#030617 100%)}
.shell{padding:10px 10px 14px}.scene{height:${if (quizMode) "300px" else "238px"};position:relative;overflow:hidden;border-radius:18px;border:1px solid #303d70;background:radial-gradient(ellipse at center,#131f49,#05091f 78%)}
canvas{width:100%;height:100%;display:block;touch-action:manipulation;touch-action:none}
.ring{position:absolute;width:30px;height:30px;border:2px solid #ffe783;border-radius:50%;box-shadow:0 0 12px #ffe783;transform:translate(-50%,-50%);pointer-events:none;display:none}
.caption{font-size:12px;color:#b8c7f9;margin:7px 2px 0}
.panel{margin-top:9px;padding:12px;border-radius:15px;background:rgba(26,36,80,.92);border:1px solid #334271}
.eyebrow{font-size:11px;color:#9db2f4;text-transform:uppercase;letter-spacing:.09em;font-weight:800}
h3{font-size:17px;margin:5px 0 8px;line-height:1.3}
p{font-size:13px;color:#d6defb;line-height:1.4;margin:5px 0 9px}
input{width:100%;height:42px;border-radius:11px;border:1px solid #6375b2;background:#080e2b;color:white;padding:0 12px;font-size:16px;outline:none}
button{border:0;border-radius:12px;background:linear-gradient(100deg,#5848ef,#3a70ed);color:white;font-weight:750;font-size:14px;padding:12px 14px;min-height:42px;width:100%;margin-top:8px}
button:disabled{opacity:.4}
.controls{display:flex;flex-wrap:wrap;gap:6px;margin-top:7px}.controls button{width:auto;flex:1 1 28%;margin-top:0;padding:9px 6px;min-width:80px;font-size:11px;border:1px solid #536fb5;background:linear-gradient(140deg,#2a3d79,#18284e)}.controls button[aria-pressed="true"]{background:linear-gradient(140deg,#087e78,#175e87);border-color:#8af0e2}
.status{min-height:23px;font-size:12px;color:#b9f2df;margin-top:7px;line-height:1.4}
.score{font-size:12px;color:#c4d3ff;text-align:right}
@media(min-width:520px){.scene{height:${if (quizMode) "360px" else "250px"}}}
</style>
</head>
<body>
<div class="shell">
  <div class="scene">
    <canvas id="scene" aria-label="Interactive 4D Solar System"></canvas>
    <div class="ring" id="selectionRing"></div>
  </div>
  <div class="controls" id="viewControls">
    <button id="zoomOut" type="button">− Zoom out</button>
    <button id="zoomIn" type="button">+ Zoom in</button>
    <button id="explodeSystem" type="button" aria-pressed="false">🧩 Explode orbits</button>
    <button id="turnSystem" type="button">⟳ 360°</button>
    <button id="resetSystem" type="button">Reset view</button>
  </div>
  <div class="caption" id="sceneCaption">Interactive 4D-style planetary model. Drag to rotate; tap any planet for facts.</div>
  <div class="panel" id="game" style="display:${if (quizMode) "block" else "none"}">
    <div class="eyebrow" id="round">Planet challenge</div>
    <h3 id="clue">Find the planet</h3>
    <p>Tap any planet to inspect its facts. In challenge mode, use the clues to identify the right world.</p>
    <input id="guess" type="text" autocomplete="off" autocapitalize="words" placeholder="Type the planet name">
    <button id="check" type="button">Check my guess</button>
    <div class="status" id="status" aria-live="polite"></div>
    <button id="next" type="button" style="display:none">Next planet</button>
    <div class="score" id="score">Score: 0 / 0</div>
  </div>
</div>
<script>
(function(){
'use strict';
const QUIZ_MODE = ${if (quizMode) "true" else "false"};
const planets = [
 {name:'Mercury',color:[0.62,0.63,0.68],orbit:0.62,radius:0.105,speed:0.85,phase:0.2,clue:'Find the smallest planet, which orbits closest to the Sun.'},
 {name:'Venus',color:[0.95,0.67,0.34],orbit:0.88,radius:0.14,speed:1.1,phase:1.7,clue:'Find the bright, golden planet often called Earth’s sister planet.'},
 {name:'Earth',color:[0.16,0.55,0.98],orbit:1.18,radius:0.15,speed:1.45,phase:3.1,clue:'Find our blue-and-green home planet with oceans and life.'},
 {name:'Mars',color:[0.93,0.28,0.16],orbit:1.48,radius:0.13,speed:1.8,phase:4.4,clue:'Find the rusty-red planet known as the Red Planet.'},
 {name:'Jupiter',color:[0.84,0.61,0.39],orbit:1.92,radius:0.245,speed:2.4,phase:0.9,clue:'Find the largest planet, with broad tan and brown cloud bands.'},
 {name:'Saturn',color:[0.92,0.78,0.48],orbit:2.36,radius:0.215,speed:2.9,phase:2.6,clue:'Find the planet with the most noticeable wide rings.'},
 {name:'Uranus',color:[0.40,0.84,0.88],orbit:2.77,radius:0.17,speed:3.4,phase:5.4,clue:'Find the pale cyan ice giant that rotates unusually tilted.'},
 {name:'Neptune',color:[0.22,0.36,0.98],orbit:3.12,radius:0.17,speed:3.9,phase:3.8,clue:'Find the deep-blue planet farthest from the Sun.'}
];
const canvas=document.getElementById('scene'), ring=document.getElementById('selectionRing');
const caption=document.getElementById('sceneCaption'), roundEl=document.getElementById('round');
const clueEl=document.getElementById('clue'), guessEl=document.getElementById('guess');
const checkBtn=document.getElementById('check'), nextBtn=document.getElementById('next');
const statusEl=document.getElementById('status'), scoreEl=document.getElementById('score');
let gl=canvas.getContext('webgl',{antialias:true,alpha:false}), ctx=null;
let selected=null,asked=[],target=null,correct=0,total=0,answered=false,complete=false;
let viewZoom=1,viewRotationX=-0.12,viewRotationY=0,viewExploded=false,dragging=false,lastTouchX=0,lastTouchY=0;
let aspectScale=1,lastPositions=[],sphereProgram=null,orbitProgram=null,sphereBuffer=null,sphereIndexBuffer=null,ringBuffer=null,orbitBuffers=[],indexCount=0;
function playCue(kind){try{const AC=window.AudioContext||window.webkitAudioContext;if(!AC)return;const ac=playCue.ac||(playCue.ac=new AC());if(ac.state==='suspended')ac.resume();const osc=ac.createOscillator(),gain=ac.createGain();osc.type=kind==='correct'?'sine':'triangle';osc.frequency.value=kind==='correct'?880:kind==='tap'?620:390;gain.gain.setValueAtTime(.0001,ac.currentTime);gain.gain.exponentialRampToValueAtTime(.055,ac.currentTime+.015);gain.gain.exponentialRampToValueAtTime(.0001,ac.currentTime+.14);osc.connect(gain);gain.connect(ac.destination);osc.start();osc.stop(ac.currentTime+.15);}catch(_){}}
function compile(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s)||'shader error');return s;}
function makeProgram(vs,fs){const p=gl.createProgram();gl.attachShader(p,compile(gl.VERTEX_SHADER,vs));gl.attachShader(p,compile(gl.FRAGMENT_SHADER,fs));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(p)||'link error');return p;}
const planetVS='attribute vec3 aPosition;attribute vec3 aNormal;uniform vec3 uCenter;uniform float uRadius;uniform float uRotation;uniform float uAspectScale;uniform float uViewX;uniform float uViewY;uniform float uZoom;varying vec3 vNormal;varying vec3 vWorld;void main(){float c=cos(uRotation),s=sin(uRotation);vec3 p=vec3(aPosition.x*c+aPosition.z*s,aPosition.y,-aPosition.x*s+aPosition.z*c);vec3 world=(uCenter+p*uRadius)*uZoom;float cy=cos(uViewY),sy=sin(uViewY),cx=cos(uViewX),sx=sin(uViewX);world=vec3(world.x*cy+world.z*sy,world.x*sx*sy+world.y*cx-world.z*sx*cy,-world.x*cx*sy+world.y*sx+world.z*cx*cy);float depth=max(2.0,8.0-world.z);gl_Position=vec4(world.x*1.65*uAspectScale/depth,world.y*2.0/depth,-world.z/9.0,1.0);vNormal=normalize(vec3(aNormal.x*c+aNormal.z*s,aNormal.y,-aNormal.x*s+aNormal.z*c));vWorld=world;}';
const planetFS='precision mediump float;uniform vec3 uColor;uniform float uSun;varying vec3 vNormal;varying vec3 vWorld;void main(){vec3 N=normalize(vNormal);vec3 L=normalize(vec3(-vWorld.x,-vWorld.y,4.5-vWorld.z));float d=max(dot(N,L),0.0);float rim=pow(1.0-max(dot(N,vec3(0.0,0.0,1.0)),0.0),2.0);vec3 c=uColor*(0.22+0.86*d)+uColor*uSun*0.48+vec3(0.05,0.10,0.24)*rim*0.3;gl_FragColor=vec4(c,1.0);}';
const lineVS='attribute vec3 aPosition;uniform float uAspectScale;uniform float uViewX;uniform float uViewY;uniform float uZoom;uniform float uExplode;void main(){vec3 world=aPosition*uZoom*uExplode;float cy=cos(uViewY),sy=sin(uViewY),cx=cos(uViewX),sx=sin(uViewX);world=vec3(world.x*cy+world.z*sy,world.x*sx*sy+world.y*cx-world.z*sx*cy,-world.x*cx*sy+world.y*sx+world.z*cx*cy);float d=max(2.0,8.0-world.z);gl_Position=vec4(world.x*1.65*uAspectScale/d,world.y*2.0/d,-world.z/9.0,1.0);}';
const lineFS='precision mediump float;uniform vec4 uLineColor;void main(){gl_FragColor=uLineColor;}';
function makeSphere(){
 const data=[],indices=[],lat=20,lon=28;
 for(let y=0;y<=lat;y++){const theta=y*Math.PI/lat,sy=Math.cos(theta),r=Math.sin(theta);for(let x=0;x<=lon;x++){const phi=x*2*Math.PI/lon,px=r*Math.cos(phi),pz=r*Math.sin(phi);data.push(px,sy,pz,px,sy,pz);}}
 for(let y=0;y<lat;y++)for(let x=0;x<lon;x++){const a=y*(lon+1)+x,b=a+lon+1;indices.push(a,b,a+1,b,b+1,a+1);}
 const vb=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,vb);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.STATIC_DRAW);
 const ib=gl.createBuffer();gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ib);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint16Array(indices),gl.STATIC_DRAW);
 return {vb,ib,count:indices.length};
}
function makeOrbit(r){const pts=[];for(let i=0;i<160;i++){const a=i*Math.PI*2/160;pts.push(Math.cos(a)*r,Math.sin(a)*r*0.31,Math.sin(a)*r*0.73);}const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(pts),gl.STATIC_DRAW);return b;}
try{if(gl){sphereProgram=makeProgram(planetVS,planetFS);orbitProgram=makeProgram(lineVS,lineFS);const b=makeSphere();sphereBuffer=b.vb;sphereIndexBuffer=b.ib;indexCount=b.count;orbitBuffers=planets.map(p=>makeOrbit(p.orbit));ringBuffer=gl.createBuffer();gl.enable(gl.DEPTH_TEST);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);}else ctx=canvas.getContext('2d');}catch(e){gl=null;ctx=canvas.getContext('2d');}
function resize(){const rect=canvas.getBoundingClientRect(),dpr=Math.min(2,window.devicePixelRatio||1),w=Math.max(1,Math.floor(rect.width*dpr)),h=Math.max(1,Math.floor(rect.height*dpr));if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}aspectScale=Math.min(1,(rect.width/Math.max(1,rect.height))/1.2);if(gl)gl.viewport(0,0,w,h);}
function planetPosition(p,t){const a=t*0.00012/p.speed+p.phase,orbit=p.orbit*(viewExploded?1.42:1);return {x:Math.cos(a)*orbit,y:Math.sin(a)*orbit*0.31,z:Math.sin(a)*orbit*0.73};}
function applyViewRotation(pos){const x=pos.x*viewZoom,y=pos.y*viewZoom,z=pos.z*viewZoom,cy=Math.cos(viewRotationY),sy=Math.sin(viewRotationY),cx=Math.cos(viewRotationX),sx=Math.sin(viewRotationX);return {x:x*cy+z*sy,y:x*sx*sy+y*cx-z*sx*cy,z:-x*cx*sy+y*sx+z*cx*cy};}
function project(pos){const v=applyViewRotation(pos),rect=canvas.getBoundingClientRect(),d=Math.max(2,8-v.z),nx=v.x*1.65*aspectScale/d,ny=v.y*2/d;return {x:(nx+1)*rect.width/2,y:(1-ny)*rect.height/2};}
function drawPlanet(pos,r,col,rot,isSun){gl.useProgram(sphereProgram);gl.bindBuffer(gl.ARRAY_BUFFER,sphereBuffer);const ap=gl.getAttribLocation(sphereProgram,'aPosition'),an=gl.getAttribLocation(sphereProgram,'aNormal');gl.enableVertexAttribArray(ap);gl.vertexAttribPointer(ap,3,gl.FLOAT,false,24,0);gl.enableVertexAttribArray(an);gl.vertexAttribPointer(an,3,gl.FLOAT,false,24,12);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,sphereIndexBuffer);gl.uniform3f(gl.getUniformLocation(sphereProgram,'uCenter'),pos.x,pos.y,pos.z);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uRadius'),r);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uRotation'),rot);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uAspectScale'),aspectScale);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uViewX'),viewRotationX);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uViewY'),viewRotationY);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uZoom'),viewZoom);gl.uniform3f(gl.getUniformLocation(sphereProgram,'uColor'),col[0],col[1],col[2]);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uSun'),isSun?1:0);gl.drawElements(gl.TRIANGLES,indexCount,gl.UNSIGNED_SHORT,0);}
function drawLines(buffer,count,col){gl.useProgram(orbitProgram);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);const a=gl.getAttribLocation(orbitProgram,'aPosition');gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,3,gl.FLOAT,false,0,0);gl.uniform1f(gl.getUniformLocation(orbitProgram,'uAspectScale'),aspectScale);gl.uniform1f(gl.getUniformLocation(orbitProgram,'uViewX'),viewRotationX);gl.uniform1f(gl.getUniformLocation(orbitProgram,'uViewY'),viewRotationY);gl.uniform1f(gl.getUniformLocation(orbitProgram,'uZoom'),viewZoom);gl.uniform1f(gl.getUniformLocation(orbitProgram,'uExplode'),viewExploded?1.42:1);gl.uniform4f(gl.getUniformLocation(orbitProgram,'uLineColor'),col[0],col[1],col[2],col[3]);gl.drawArrays(gl.LINE_LOOP,0,count);}
function drawGL(t){gl.clearColor(0.015,0.024,0.075,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.depthMask(false);orbitBuffers.forEach(b=>drawLines(b,160,[0.28,0.39,0.72,0.48]));const sat=planetPosition(planets[5],t),rp=[];for(let i=0;i<100;i++){const a=i*Math.PI*2/100;rp.push(sat.x+Math.cos(a)*0.31,sat.y+Math.sin(a)*0.07,sat.z+Math.sin(a)*0.16);}gl.bindBuffer(gl.ARRAY_BUFFER,ringBuffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(rp),gl.DYNAMIC_DRAW);drawLines(ringBuffer,100,[0.93,0.78,0.47,0.95]);gl.depthMask(true);drawPlanet({x:0,y:0,z:0},0.43,[1.0,0.57,0.12],t*0.0001,true);lastPositions=[];planets.forEach(p=>{const pos=planetPosition(p,t);lastPositions.push({name:p.name,pos:pos,screen:project(pos),radius:p.radius});drawPlanet(pos,p.radius,p.color,t*0.0007/p.speed,false);});}
function drawFallback(t){if(!ctx)return;const w=canvas.width,h=canvas.height,cx=w/2,cy=h/2,scale=Math.min(w/7.4,h/3.7);ctx.clearRect(0,0,w,h);ctx.fillStyle='#05091f';ctx.fillRect(0,0,w,h);ctx.strokeStyle='rgba(107,133,216,.35)';ctx.lineWidth=1;planets.forEach(p=>{ctx.beginPath();ctx.ellipse(cx,cy,p.orbit*scale,p.orbit*scale*.34,0,0,Math.PI*2);ctx.stroke();});const sg=ctx.createRadialGradient(cx-6,cy-6,2,cx,cy,scale*.43);sg.addColorStop(0,'#fff5a6');sg.addColorStop(.5,'#ffab36');sg.addColorStop(1,'#e54b0c');ctx.fillStyle=sg;ctx.beginPath();ctx.arc(cx,cy,scale*.43,0,Math.PI*2);ctx.fill();lastPositions=[];planets.forEach(p=>{const pos=planetPosition(p,t),x=cx+pos.x*scale,y=cy+pos.y*scale;lastPositions.push({name:p.name,pos:pos,screen:{x:x/(window.devicePixelRatio||1),y:y/(window.devicePixelRatio||1)},radius:p.radius});const r=Math.max(4,p.radius*scale),col='rgb('+Math.round(p.color[0]*255)+','+Math.round(p.color[1]*255)+','+Math.round(p.color[2]*255)+')';const g=ctx.createRadialGradient(x-r*.4,y-r*.4,1,x,y,r);g.addColorStop(0,'#ffffff');g.addColorStop(.24,col);g.addColorStop(1,'#080d25');ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();if(p.name==='Saturn'){ctx.strokeStyle='#e2cf99';ctx.beginPath();ctx.ellipse(x,y,r*1.8,r*.55,-.24,0,Math.PI*2);ctx.stroke();}});}
function frame(t){resize();if(gl)drawGL(t);else drawFallback(t);if(selected){const p=lastPositions.find(x=>x.name===selected);if(p){const rect=canvas.getBoundingClientRect();ring.style.display='block';ring.style.left=p.screen.x+'px';ring.style.top=p.screen.y+'px';ring.style.width=Math.max(28,p.radius*viewZoom*rect.width*.16)+'px';ring.style.height=ring.style.width;}else ring.style.display='none';}requestAnimationFrame(frame);}
canvas.addEventListener('click',function(e){const rect=canvas.getBoundingClientRect(),x=e.clientX-rect.left,y=e.clientY-rect.top;let nearest=null,best=Infinity;lastPositions.forEach(p=>{const dx=p.screen.x-x,dy=p.screen.y-y,d=Math.sqrt(dx*dx+dy*dy);if(d<best){best=d;nearest=p;}});if(nearest&&best<Math.max(24,nearest.radius*viewZoom*rect.width*.15)){selected=nearest.name;playCue('tap');const facts={Mercury:'Smallest planet; its short year lasts 88 Earth days.',Venus:'A thick carbon-dioxide atmosphere makes Venus the hottest planet.',Earth:'Liquid-water oceans and a protective atmosphere support known life.',Mars:'Iron-rich dust gives Mars its rusty red colour.',Jupiter:'Largest planet; its Great Red Spot is a giant storm.',Saturn:'Its spectacular rings are made mostly of ice and rocky particles.',Uranus:'An ice giant that rotates on its side.',Neptune:'A distant ice giant with very fast winds.'};caption.textContent=nearest.name+' — '+(facts[nearest.name]||'Our star supplies light and energy to the Solar System.');if(QUIZ_MODE&&!answered)statusEl.textContent='Selected '+nearest.name+'. Read its facts, then type the planet name.';}});
function normalize(s){return String(s||'').toLowerCase().replace(/[^a-z]/g,'');}
function updateScore(){scoreEl.textContent='Score: '+correct+' / '+total;}
function nextQuestion(){if(asked.length>=planets.length){complete=true;clueEl.textContent='Solar System Explorer complete!';roundEl.textContent='Challenge complete';statusEl.textContent='You scored '+correct+' out of '+total+'. Play again to practise all eight planets.';checkBtn.style.display='none';nextBtn.textContent='Play again';nextBtn.style.display='block';guessEl.disabled=true;updateScore();return;}const remaining=planets.filter(p=>!asked.includes(p.name));target=remaining[Math.floor(Math.random()*remaining.length)];asked.push(target.name);selected=null;answered=false;guessEl.value='';guessEl.disabled=false;checkBtn.disabled=false;checkBtn.style.display='block';nextBtn.style.display='none';ring.style.display='none';roundEl.textContent='Planet challenge '+asked.length+' of '+planets.length;clueEl.textContent=target.clue;statusEl.textContent='Tap the matching planet in the scene, then type its name.';caption.textContent='Tap a planet to select it. Planet names are hidden.';updateScore();}
if(QUIZ_MODE){nextQuestion();checkBtn.addEventListener('click',function(){if(answered)return;const guess=normalize(guessEl.value);if(!selected||!guess){statusEl.textContent='Tap a planet and type its name before checking.';return;}answered=true;total++;const ok=selected===target.name&&guess===normalize(target.name);if(ok){correct++;playCue('correct');statusEl.textContent='Correct! '+target.name+' is a great discovery.';}else{playCue('tap');statusEl.textContent='Good try. The answer was '+target.name+'. Keep exploring!';}guessEl.disabled=true;checkBtn.disabled=true;nextBtn.style.display='block';nextBtn.textContent=asked.length>=planets.length?'See final score':'Next planet';updateScore();});nextBtn.addEventListener('click',function(){if(complete){asked=[];correct=0;total=0;complete=false;nextQuestion();}else nextQuestion();});}else{document.getElementById('game').style.display='none';caption.textContent='Explore the moving solar system. Tap a planet to highlight it.';}
const zoomIn=document.getElementById('zoomIn'),zoomOut=document.getElementById('zoomOut'),explodeBtn=document.getElementById('explodeSystem'),turnBtn=document.getElementById('turnSystem'),resetBtn=document.getElementById('resetSystem');
zoomIn.addEventListener('click',()=>{viewZoom=Math.min(2.1,+(viewZoom+.15).toFixed(2));playCue('tap');caption.textContent='Zoom '+Math.round(viewZoom*100)+'%. Drag the model to inspect it from different angles.';});
zoomOut.addEventListener('click',()=>{viewZoom=Math.max(.55,+(viewZoom-.15).toFixed(2));playCue('tap');caption.textContent='Zoom '+Math.round(viewZoom*100)+'%. Drag the model to inspect it from different angles.';});
explodeBtn.addEventListener('click',()=>{viewExploded=!viewExploded;playCue('tap');explodeBtn.setAttribute('aria-pressed',String(viewExploded));explodeBtn.textContent=viewExploded?'🧩 Reassemble orbits':'🧩 Explode orbits';caption.textContent=viewExploded?'Exploded orbital view: planetary orbits are spread apart to compare their order and spacing.':'Assembled planetary orbits restored.';});
turnBtn.addEventListener('click',()=>{playCue('tap');const start=viewRotationY,end=start+Math.PI*2,duration=1300,startTime=performance.now();function spin(now){const t=Math.min(1,(now-startTime)/duration);viewRotationY=start+(end-start)*(t*t*(3-2*t));if(t<1)requestAnimationFrame(spin);}requestAnimationFrame(spin);caption.textContent='Complete 360° rotation — watch planet positions change in perspective.';});
resetBtn.addEventListener('click',()=>{playCue('tap');viewZoom=1;viewRotationX=-0.12;viewRotationY=0;viewExploded=false;explodeBtn.setAttribute('aria-pressed','false');explodeBtn.textContent='🧩 Explode orbits';caption.textContent='Interactive 4D-style planetary model. Drag to rotate; tap any planet for facts.';});
canvas.addEventListener('pointerdown',e=>{dragging=true;lastTouchX=e.clientX;lastTouchY=e.clientY;canvas.setPointerCapture(e.pointerId);});
canvas.addEventListener('pointermove',e=>{if(!dragging)return;viewRotationY+=(e.clientX-lastTouchX)*.012;viewRotationX=Math.max(-1.1,Math.min(1.1,viewRotationX+(e.clientY-lastTouchY)*.012));lastTouchX=e.clientX;lastTouchY=e.clientY;});
canvas.addEventListener('pointerup',()=>{dragging=false;});
canvas.addEventListener('pointercancel',()=>{dragging=false;});
requestAnimationFrame(frame);
})();
</script>
</body>
</html>
""".trimIndent()


@Composable
fun Learning3DPreview(modelType: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(450.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.rgb(7, 12, 34))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                webViewClient = WebViewClient()
                tag = modelType
                loadDataWithBaseURL("https://edulock.local/", learning3DHtml(modelType), "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            if (view.tag != modelType) {
                view.tag = modelType
                view.loadDataWithBaseURL("https://edulock.local/", learning3DHtml(modelType), "text/html", "UTF-8", null)
            }
        }
    )
}

private fun learning3DHtml(modelType: String): String {
    val rawModel = modelType.lowercase().filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "geometry" }
    val model = when (rawModel) {
        "social-studies" -> "earth"
        "computer-skills" -> "circuit"
        "health-safety" -> "cell"
        "art-design", "drawing-colour" -> "flower"
        "english", "urdu", "sindhi", "hindi",
        "faith-islamic", "faith-hindu", "faith-christian", "faith-sikh",
        "faith-buddhist", "faith-jewish", "faith-jain", "faith-world" -> "book"
        else -> rawModel
    }
    val title: String
    val description: String
    val shape: String
    when (model) {
        "atom" -> {
            title = "Atom explorer"; description = "The nucleus is central, with electron paths around it."
            shape = """<div class="atom"><div class="nucleus"></div><div class="orbit orbit1"><b></b></div><div class="orbit orbit2"><b></b></div><div class="orbit orbit3"><b></b></div></div>"""
        }
        "molecule" -> {
            title = "Molecule explorer"; description = "Atoms connect through bonds; red represents oxygen."
            shape = """<div class="molecule"><i class="bond b1"></i><i class="bond b2"></i><b class="sphere oxygen"></b><b class="sphere hydrogen h1"></b><b class="sphere hydrogen h2"></b></div>"""
        }
        "geometry" -> {
            title = "3D solid explorer"; description = "Rotate the solid in perspective, pinch to zoom and explode its faces."
            shape = """<div class="cube"><i class="face front"></i><i class="face back"></i><i class="face right"></i><i class="face left"></i><i class="face top"></i><i class="face bottom"></i></div><div class="shape-label">CUBE · 6 FACES</div>"""
        }
        "tesseract" -> {
            title = "Tesseract • 4D hypercube projection"
            description = "A 4D hypercube projected into 3D: two cubes linked by eight edges. The inner cube rotates on a second axis to make the changing 4D projection easier to explore."
            shape = """<div class="tesseract"><div class="hyper-outer"><i class="hyper-face hfront"></i><i class="hyper-face hback"></i><i class="hyper-face hright"></i><i class="hyper-face hleft"></i><i class="hyper-face htop"></i><i class="hyper-face hbottom"></i></div><div class="hyper-inner"><i class="inner-face ifront"></i><i class="inner-face iback"></i><i class="inner-face iright"></i><i class="inner-face ileft"></i><i class="inner-face itop"></i><i class="inner-face ibottom"></i></div><i class="hyper-link link1"></i><i class="hyper-link link2"></i><i class="hyper-link link3"></i><i class="hyper-link link4"></i><i class="hyper-link link5"></i><i class="hyper-link link6"></i><i class="hyper-link link7"></i><i class="hyper-link link8"></i></div><div class="shape-label">TESSERACT · 4D → 3D PROJECTION</div>"""
        }
        "sphere" -> {
            title = "3D sphere explorer"; description = "A shaded sphere rotates in perspective; pinch to zoom and inspect its curved surface."
            shape = """<div class="solid-sphere"></div><div class="shape-label">SPHERE · 1 CURVED SURFACE</div>"""
        }
        "cylinder" -> {
            title = "3D cylinder explorer"; description = "Inspect the curved wall and the two circular ends from different angles."
            shape = """<div class="solid-cylinder"><i class="cylinder-top"></i><i class="cylinder-bottom"></i></div><div class="shape-label">CYLINDER · 2 CIRCLES</div>"""
        }
        "cone" -> {
            title = "3D cone explorer"; description = "Rotate the cone to see its apex, curved side and circular base."
            shape = """<div class="solid-cone"><i class="cone-base"></i></div><div class="shape-label">CONE · 1 APEX</div>"""
        }
        "cuboid" -> {
            title = "3D cuboid explorer"; description = "A rectangular prism with six faces; rotate to compare its length, width and height."
            shape = """<div class="cuboid"><i class="cuboid-face cfront"></i><i class="cuboid-face cback"></i><i class="cuboid-face cright"></i><i class="cuboid-face cleft"></i><i class="cuboid-face ctop"></i><i class="cuboid-face cbottom"></i></div><div class="shape-label">CUBOID · 6 FACES</div>"""
        }
        "prism" -> {
            title = "3D triangular prism"; description = "Explore two triangular ends joined by three rectangular faces."
            shape = """<div class="tri-prism"><i class="prism-face pf1"></i><i class="prism-face pf2"></i><i class="prism-face pf3"></i><i class="prism-face pf4"></i><i class="prism-face pf5"></i></div><div class="shape-label">TRIANGULAR PRISM</div>"""
        }
        "cell" -> {
            title = "Cell explorer"; description = "Illustrative animal cell with membrane, nucleus and organelles."
            shape = """<div class="cell"><b class="membrane"></b><b class="cell-nucleus"></b><i class="organelle o1"></i><i class="organelle o2"></i><i class="organelle o3"></i><i class="organelle o4"></i></div>"""
        }
        "force" -> {
            title = "Forces & motion • 3D ramp"
            description = "A shaded ball rolls on a raised, solid ramp. Drag to rotate around the scene and pinch to zoom."
            shape = """<div class="force-floor"></div><div class="ramp"><i class="ramp-side"></i></div><div class="ball"></div><div class="arrow arrow1">➜</div><div class="arrow arrow2">⬆</div>"""
        }
        "friction", "rough-surface" -> {
            title = if (model == "friction") "Friction • textured track" else "Rough surface • surface contact"
            description = "A 3D block moves across a layered track. The roughness and motion trails change as the scene animates."
            shape = """<div class="friction-scene"><i class="track track-back"></i><i class="track track-top"></i><i class="friction-block"></i><i class="motion-trail trail-a"></i><i class="motion-trail trail-b"></i><i class="friction-particle fp-a"></i><i class="friction-particle fp-b"></i><i class="friction-particle fp-c"></i></div>"""
        }
        "force-meter" -> {
            title = "Force meter • spring scale"
            description = "Watch a mass pull down a spring; the scale represents measured force."
            shape = """<div class="force-meter"><i class="meter-case"></i><i class="meter-scale"></i><i class="meter-spring"></i><i class="meter-hook"></i><i class="meter-mass"></i></div>"""
        }
        "motion-cart" -> {
            title = "Motion cart • acceleration"
            description = "A 3D cart travels along a raised track while the force arrow indicates its direction."
            shape = """<div class="cart-scene"><i class="cart-track"></i><i class="cart-body"></i><i class="cart-wheel wheel-a"></i><i class="cart-wheel wheel-b"></i><i class="cart-push-arrow">➜</i></div>"""
        }
        "energy-ball" -> {
            title = "Stored energy • height"
            description = "A shaded ball rises between platforms. Its height represents gravitational potential energy."
            shape = """<div class="energy-scene"><i class="energy-platform platform-low"></i><i class="energy-platform platform-high"></i><i class="energy-ball"></i><i class="energy-arrow">↑</i></div>"""
        }
        "broken-circuit" -> {
            title = "Open circuit • broken connection"
            description = "A 3D circuit shows a visible gap. The lamp remains dim until the conducting path is complete."
            shape = """<div class="circuit broken"><i class="wire top"></i><i class="wire right"></i><i class="wire bottom"></i><i class="wire left"></i><i class="wire gap-left"></i><b class="battery"></b><b class="lamp"></b><i class="spark-gap"></i></div>"""
        }
        "inertia" -> {
            title = "Inertia • objects with different masses"
            description = "Two 3D objects respond differently to a push; more mass means greater resistance to a change in motion."
            shape = """<div class="inertia-scene"><i class="inertia-track"></i><i class="mass-object mass-light"></i><i class="mass-object mass-heavy"></i><i class="mass-arrow">➜</i></div>"""
        }
        "chemistry-lab" -> {
            title = "Interactive chemistry reaction lab"
            description = "Choose a virtual reaction, then mix the simulated liquids. Watch a product form through colour change and gas bubbles. This is a visual simulation, not an instruction to mix real chemicals."
            shape = """<div class="chemistry-lab" id="chemistry-lab"><i class="lab-platform"></i><div class="beaker beaker-a"><i class="beaker-rim"></i><i class="liquid liquid-a"></i><i class="beaker-glass"></i><span class="beaker-label label-a">A</span></div><div class="mix-symbol">+</div><div class="beaker beaker-b"><i class="beaker-rim"></i><i class="liquid liquid-b"></i><i class="beaker-glass"></i><span class="beaker-label label-b">B</span></div><div class="mix-arrow">➜</div><div class="beaker beaker-result"><i class="beaker-rim"></i><i class="liquid liquid-result"></i><i class="beaker-glass"></i><i class="gas-bubble bubble-a"></i><i class="gas-bubble bubble-b"></i><i class="gas-bubble bubble-c"></i><span class="beaker-label label-result">Product</span></div><i class="reaction-glow"></i></div>"""
        }
        "acid-base" -> {
            title = "Acid and base • indicator colour"
            description = "Virtual indicator drops enter a beaker and the solution changes colour to show acidity."
            shape = """<div class="acid-scene"><i class="lab-platform"></i><div class="beaker"><i class="beaker-rim"></i><i class="liquid indicator-liquid"></i><i class="beaker-glass"></i></div><i class="indicator-drop drop-a"></i><i class="indicator-drop drop-b"></i><i class="indicator-glow"></i></div>"""
        }
        "gas-mixture" -> {
            title = "Gas mixture • particles in 3D space"
            description = "Different gas particles move through a transparent 3D chamber to illustrate a mixture."
            shape = """<div class="gas-chamber"><i class="chamber-edge"></i><i class="gas-particle gas-a"></i><i class="gas-particle gas-b"></i><i class="gas-particle gas-c"></i><i class="gas-particle gas-d"></i><i class="gas-particle gas-e"></i><i class="gas-particle gas-f"></i></div>"""
        }
        "periodic-table" -> {
            title = "Periodic table • element blocks"
            description = "Glowing element tiles rise in depth, representing the organised pattern of chemical elements."
            shape = """<div class="element-table"><i class="element-tile e-tile-a">H</i><i class="element-tile e-tile-b">C</i><i class="element-tile e-tile-c">N</i><i class="element-tile e-tile-d">O</i><i class="element-tile e-tile-e">Na</i><i class="element-tile e-tile-f">Cl</i><i class="element-tile e-tile-g">Fe</i><i class="element-tile e-tile-h">He</i></div>"""
        }
        "nucleus", "proton" -> {
            title = if (model == "proton") "Positive proton • nucleus model" else "Atomic nucleus • protons and neutrons"
            description = "Inspect the clustered particles at the centre of an atom in a rotating 3D model."
            shape = """<div class="nucleus-model"><i class="nucleus-particle np-a"></i><i class="nucleus-particle np-b"></i><i class="nucleus-particle np-c"></i><i class="nucleus-particle np-d"></i><i class="nucleus-orbit"></i><i class="charge-dot"></i></div>"""
        }
        "molecule-vibration" -> {
            title = "Molecule vibration • bonds in motion"
            description = "Three shaded atoms move around flexible bonds to illustrate molecular motion."
            shape = """<div class="vibrating-molecule"><i class="vib-bond vb-a"></i><i class="vib-bond vb-b"></i><i class="vib-atom atom-a"></i><i class="vib-atom atom-b"></i><i class="vib-atom atom-c"></i></div>"""
        }
        "water-cycle" -> {
            title = "Water-cycle explorer"; description = "Water evaporates, cools and returns as precipitation."
            shape = """<div class="sun"></div><div class="cloud cloud1"></div><div class="cloud cloud2"></div><div class="drop d1"></div><div class="drop d2"></div><div class="drop d3"></div><div class="ocean"></div><div class="vapour"></div>"""
        }
        "earth" -> {
            title = "Earth explorer"; description = "A rotating globe illustrates the day/night cycle."
            shape = """<div class="earth"><i class="land l1"></i><i class="land l2"></i><i class="land l3"></i><i class="atmosphere"></i></div>"""
        }
        "circuit" -> {
            title = "Circuit explorer"; description = "Animated current demonstrates a closed electric circuit."
            shape = """<div class="circuit"><i class="wire top"></i><i class="wire right"></i><i class="wire bottom"></i><i class="wire left"></i><b class="battery"></b><b class="lamp"></b><i class="electron e1"></i><i class="electron e2"></i><i class="electron e3"></i></div>"""
        }
        "magnet" -> {
            title = "Magnet field explorer"; description = "A horseshoe magnet labels the north and south poles."
            shape = """<div class="magnet"><i class="magnetbody"></i><b class="north">N</b><b class="south">S</b><i class="fieldline f1"></i><i class="fieldline f2"></i><i class="fieldline f3"></i></div>"""
        }
        "volcano" -> {
            title = "Volcano explorer"; description = "Inspect the crater, lava and volcanic rock layers."
            shape = """<div class="volcano"><i class="mountain"></i><i class="crater"></i><i class="lavaflow"></i><i class="rocklayer layer1"></i><i class="rocklayer layer2"></i></div>"""
        }
        "flower" -> {
            title = "Flower anatomy"; description = "Touch the petals, centre, stem and leaves."
            shape = """<div class="flower"><i class="stem"></i><i class="leaf leaf1"></i><i class="leaf leaf2"></i><i class="petal p1"></i><i class="petal p2"></i><i class="petal p3"></i><i class="petal p4"></i><i class="petal p5"></i><i class="petal p6"></i><b class="flowercenter"></b></div>"""
        }
        "book" -> {
            title = "Learning book explorer"; description = "Open the layers of a learning book and inspect pages, cover and spine."
            shape = """<div class="book"><i class="book-cover"></i><i class="page-left"></i><i class="page-right"></i><i class="spine"></i><i class="bookmark"></i><b class="book-title">EXPLORE</b></div>"""
        }
        "cube-volume", "cube-scale", "box-volume" -> {
            title = when (model) { "cube-volume" -> "Cube volume • edge layers"; "cube-scale" -> "Cube scale • changing dimensions"; else -> "Box volume • length × width × height" }
            description = "A raised 3D solid rotates as its dimensions are inspected."
            shape = """<div class="cuboid volume-solid"><i class="cuboid-face cfront"></i><i class="cuboid-face cback"></i><i class="cuboid-face cright"></i><i class="cuboid-face cleft"></i><i class="cuboid-face ctop"></i><i class="cuboid-face cbottom"></i><i class="volume-edge"></i></div>"""
        }
        else -> {
            val prettyModel = model.split("-").joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            title = "${prettyModel} • 3D explorer"
            description = "Rotate the depth-layered model, pinch to zoom and tap its parts. Animation shows the concept changing over time."
            shape = """<div class="topic-model $model"><i class="topic-ground"></i><i class="topic-ring"></i><i class="topic-node node-a"></i><i class="topic-node node-b"></i><i class="topic-node node-c"></i><i class="topic-particle particle-a"></i><i class="topic-particle particle-b"></i><i class="topic-core"></i></div><div class="shape-label">${prettyModel.uppercase()}</div>"""
        }
    }
    val chemistryControls = if (model == "chemistry-lab") """
<div class="controls reaction-controls">
<button id="mix-indicator">🧪 Mix indicator</button>
<button id="mix-gas">🫧 Simulate gas reaction</button>
<button id="reset-beakers">↺ Reset lab</button>
</div>
<div class="reaction-note">Virtual chemicals only • No real chemicals are mixed.</div>
""" else ""
    return """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;background:#070c22;color:#f5f7ff;font-family:system-ui,sans-serif}
.wrap{padding:9px}.stage{height:228px;position:relative;overflow:hidden;border:1px solid #4864a8;border-radius:18px;background:radial-gradient(ellipse at 50% 38%,#344b8b 0%,#141f4d 52%,#070c22 100%);perspective:900px;touch-action:none;box-shadow:inset 0 0 42px #809fff18}.model-root{position:absolute;inset:0;transform:scale(1) rotateX(0deg) rotateY(0deg);transform-style:preserve-3d;transform-origin:50% 50%;transition:transform 180ms linear}.stage *{transition:transform 420ms cubic-bezier(.2,.75,.25,1),filter 300ms ease}
.star{position:absolute;width:2px;height:2px;border-radius:50%;background:#dce7ff;box-shadow:30px 26px #dce7ff,72px 93px #9db4f5,145px 20px #fff,210px 120px #dce7ff,260px 46px #8faaff,330px 85px #fff,385px 27px #dce7ff,410px 135px #b5c5ff}
.caption{padding:8px 3px 5px;color:#bfd0ff;font-size:12px;line-height:1.45}.title{font-size:15px;font-weight:900;color:#f7f4ff}.controls{display:flex;flex-wrap:wrap;gap:6px;margin-top:7px}.controls button{flex:1 1 27%;min-width:78px;padding:9px 7px;border-radius:11px;border:1px solid #536fb5;background:linear-gradient(140deg,#2a3d79,#18284e);color:#eff5ff;font-weight:750;font-size:11px}.controls button:active{transform:scale(.96)}.controls button[aria-pressed="true"]{background:linear-gradient(140deg,#087e78,#175e87);border-color:#8af0e2}.detail{margin-top:7px;min-height:58px;padding:10px 12px;background:linear-gradient(135deg,#172650,#142c48);border:1px solid #3a6191;border-radius:13px;color:#dceaff;font-size:12px;line-height:1.5}.detail strong{color:#82f0db;font-size:13px}.stage.exploded .nucleus{transform:translateZ(23px) scale(1.25)}.stage.exploded .orbit{transform:translateZ(12px) rotateX(65deg) scale(1.12)}.stage.exploded .oxygen{transform:translateZ(25px) translateY(-8px) scale(1.14)}.stage.exploded .hydrogen.h1{transform:translate(-16px,14px) translateZ(30px) scale(1.1)}.stage.exploded .hydrogen.h2{transform:translate(16px,14px) translateZ(30px) scale(1.1)}.stage.exploded .bond{transform:translateZ(15px) scale(.78)}.stage.exploded .face.front{transform:translateZ(63px)}.stage.exploded .face.back{transform:rotateY(180deg) translateZ(63px)}.stage.exploded .face.right{transform:rotateY(90deg) translateZ(63px)}.stage.exploded .face.left{transform:rotateY(-90deg) translateZ(63px)}.stage.exploded .face.top{transform:rotateX(90deg) translateZ(63px)}.stage.exploded .face.bottom{transform:rotateX(-90deg) translateZ(63px)}.stage.exploded .cell-nucleus{transform:translateZ(28px) scale(1.2)}.stage.exploded .organelle{transform:translateZ(22px) scale(1.25)}.stage.exploded .membrane{transform:scale(1.08)}.stage.exploded .north{transform:translate(-13px,-10px) translateZ(20px)}.stage.exploded .south{transform:translate(13px,-10px) translateZ(20px)}.stage.exploded .fieldline{transform:translateZ(18px) scale(1.14)}.stage.exploded .lavaflow{transform:translate(18px,6px) rotate(-16deg) scale(1.1)}.stage.exploded .crater{transform:translateY(-11px) scale(1.15)}.stage.exploded .rocklayer{transform:translateY(15px) translateZ(18px)}.stage.exploded .petal{filter:brightness(1.15);transform:rotate(var(--rotation,0deg)) translateY(-12px) translateZ(20px) scale(1.1)}.stage.exploded .flowercenter{transform:translateZ(26px) scale(1.2)}.stage.exploded .leaf{transform:translateZ(17px) scale(1.15)}.stage.exploded .battery,.stage.exploded .lamp{transform:translateZ(20px) scale(1.18)}.stage.exploded .wire{transform:translateZ(10px)}
.atom,.molecule,.cube,.cell,.earth,.circuit,.magnet,.volcano,.flower,.book{position:absolute;left:50%;top:49%;transform:translate(-50%,-50%);transform-style:preserve-3d}
.atom{width:128px;height:110px;animation:float 4s ease-in-out infinite}.nucleus{position:absolute;left:54px;top:44px;width:25px;height:25px;border-radius:50%;background:radial-gradient(circle at 30% 25%,#ffc2ba,#e64756 42%,#67184f);box-shadow:0 0 19px #f05c9b}
.orbit{position:absolute;left:9px;top:24px;width:110px;height:60px;border:2px solid #65d9ff;border-radius:50%;transform-style:preserve-3d}.orbit1{transform:rotateX(65deg) rotateZ(22deg)}.orbit2{transform:rotateX(65deg) rotateZ(82deg)}.orbit3{transform:rotateX(65deg) rotateZ(143deg)}.orbit b{position:absolute;left:90%;top:18%;width:12px;height:12px;background:radial-gradient(circle at 30% 20%,white,#46c8ff 55%,#1e53a8);border-radius:50%;box-shadow:0 0 12px #42d6ff;animation:electron 2s linear infinite}.orbit2 b{animation-delay:-.65s}.orbit3 b{animation-delay:-1.2s}
.molecule{width:175px;height:118px;animation:float 4s ease-in-out infinite}.sphere{position:absolute;border-radius:50%;box-shadow:inset -10px -10px 15px #02061766,0 0 16px #b6ccff44}.oxygen{width:56px;height:56px;left:60px;top:28px;background:radial-gradient(circle at 28% 22%,#fff0ed,#f14f57 48%,#8d193b)}.hydrogen{width:35px;height:35px;background:radial-gradient(circle at 28% 20%,white,#d8e2ed 55%,#61718d)}.h1{left:22px;top:66px}.h2{left:119px;top:66px}.bond{position:absolute;width:58px;height:9px;background:linear-gradient(#c8e2fc,#4a6d98);top:70px;border-radius:9px;transform-style:preserve-3d}.b1{left:34px;transform:rotate(36deg)}.b2{left:91px;transform:rotate(-36deg)}
.cube{width:94px;height:94px;animation:spin 9s linear infinite}.face{position:absolute;width:94px;height:94px;border:2px solid #e6edff;background:linear-gradient(135deg,#7a64ffb8,#24c6dc73);box-shadow:inset 0 0 25px #d9e4ff30}.front{transform:translateZ(47px)}.back{transform:rotateY(180deg) translateZ(47px)}.right{transform:rotateY(90deg) translateZ(47px)}.left{transform:rotateY(-90deg) translateZ(47px)}.top{transform:rotateX(90deg) translateZ(47px)}.bottom{transform:rotateX(-90deg) translateZ(47px)}.shape-label{position:absolute;top:83%;left:50%;transform:translateX(-50%);font-size:10px;font-weight:900;letter-spacing:.12em;color:#c3d5ff}
.tesseract{position:absolute;left:50%;top:47%;width:150px;height:145px;transform:translate(-50%,-50%);transform-style:preserve-3d;animation:rotate4d 8s linear infinite}
.hyper-outer,.hyper-inner{position:absolute;left:50%;top:50%;transform-style:preserve-3d}.hyper-outer{width:94px;height:94px;transform:translate(-50%,-50%) rotateX(-17deg) rotateY(-22deg)}.hyper-face{position:absolute;width:94px;height:94px;border:2px solid #a6e8ff;background:linear-gradient(135deg,#2776d166,#a36aff30);box-shadow:inset 0 0 18px #83c7ff36,0 0 9px #55cfff24;backface-visibility:visible}.hfront{transform:translateZ(47px)}.hback{transform:rotateY(180deg) translateZ(47px)}.hright{transform:rotateY(90deg) translateZ(47px)}.hleft{transform:rotateY(-90deg) translateZ(47px)}.htop{transform:rotateX(90deg) translateZ(47px)}.hbottom{transform:rotateX(-90deg) translateZ(47px)}
.hyper-inner{width:56px;height:56px;transform:translate(-50%,-50%) translateZ(8px) rotateX(24deg) rotateY(28deg);animation:rotateInner4d 5s ease-in-out infinite alternate}.inner-face{position:absolute;width:56px;height:56px;border:2px solid #ffe8a3;background:linear-gradient(140deg,#ffad5a8c,#f06fbd38);box-shadow:inset 0 0 12px #ffd58b38}.ifront{transform:translateZ(28px)}.iback{transform:rotateY(180deg) translateZ(28px)}.iright{transform:rotateY(90deg) translateZ(28px)}.ileft{transform:rotateY(-90deg) translateZ(28px)}.itop{transform:rotateX(90deg) translateZ(28px)}.ibottom{transform:rotateX(-90deg) translateZ(28px)}
.hyper-link{position:absolute;width:44px;height:3px;left:50%;top:50%;border-radius:4px;background:linear-gradient(90deg,#82f3e6,#ffe69d);box-shadow:0 0 8px #88f4ff;transform-origin:0 50%}.link1{transform:translate(-46px,-46px) rotate(35deg)}.link2{transform:translate(46px,-46px) rotate(145deg)}.link3{transform:translate(-46px,46px) rotate(-35deg)}.link4{transform:translate(46px,46px) rotate(-145deg)}.link5{transform:translate(-46px,-46px) rotate(58deg) scaleX(.75)}.link6{transform:translate(46px,-46px) rotate(122deg) scaleX(.75)}.link7{transform:translate(-46px,46px) rotate(-58deg) scaleX(.75)}.link8{transform:translate(46px,46px) rotate(-122deg) scaleX(.75)}
.solid-sphere{position:absolute;left:50%;top:47%;width:112px;height:112px;transform:translate(-50%,-50%);border-radius:50%;background:radial-gradient(circle at 27% 22%,#fff7c5 0%,#7ad9ff 10%,#4778ed 35%,#252b79 72%,#090f35 100%);box-shadow:inset -19px -16px 22px #020617b8,inset 8px 5px 14px #e2f4ff65,0 10px 24px #0008;animation:spinSphere 12s linear infinite}
.solid-cylinder{position:absolute;left:50%;top:47%;width:76px;height:88px;transform:translate(-50%,-50%) rotateX(-12deg);transform-style:preserve-3d;border-radius:50%/12%;background:linear-gradient(90deg,#143b9b,#55c8ff 22%,#a7f4ff 45%,#4c75e9 72%,#18286a);box-shadow:inset -12px 0 18px #070d35a8,0 10px 22px #0007;animation:spinSphere 12s linear infinite}.cylinder-top,.cylinder-bottom{position:absolute;left:0;width:100%;height:27px;border-radius:50%;border:2px solid #a9efff;background:radial-gradient(ellipse at 33% 25%,#efffff,#4eb8f2 55%,#2251b8);transform:translateZ(1px)}.cylinder-top{top:-1px}.cylinder-bottom{bottom:-1px;transform:translateZ(-1px)}
.solid-cone{position:absolute;left:50%;top:46%;width:100px;height:106px;transform:translate(-50%,-50%) rotateX(-10deg);transform-style:preserve-3d;filter:drop-shadow(0 12px 13px #02061799);animation:spinSphere 12s linear infinite}.solid-cone:before{content:'';position:absolute;left:4px;top:6px;width:0;height:0;border-left:46px solid transparent;border-right:46px solid transparent;border-bottom:94px solid #6e69e8;filter:drop-shadow(inset 0 0 5px #fff)}.solid-cone:after{content:'';position:absolute;left:4px;top:84px;width:92px;height:24px;border-radius:50%;background:radial-gradient(ellipse at 35% 25%,#c4f3ff,#5e91ff 62%,#27306f);border:2px solid #b8e9ff;transform:rotateX(8deg)}.cone-base{position:absolute;left:4px;top:84px;width:92px;height:24px;border-radius:50%;border:2px solid #b8e9ff;transform:translateZ(-8px)}
.cuboid{position:absolute;left:50%;top:47%;width:110px;height:80px;transform:translate(-50%,-50%) rotateX(-18deg) rotateY(-28deg);transform-style:preserve-3d;animation:spin 10s linear infinite}.cuboid-face{position:absolute;width:110px;height:80px;border:2px solid #d7eeff;background:linear-gradient(135deg,#3d8bdbb8,#7d67ff8f);box-shadow:inset 0 0 16px #e4f4ff33}.cfront{transform:translateZ(22px)}.cback{transform:rotateY(180deg) translateZ(22px)}.cright{transform:rotateY(90deg) translateZ(55px) scaleX(.4)}.cleft{transform:rotateY(-90deg) translateZ(55px) scaleX(.4)}.ctop{transform:rotateX(90deg) translateZ(40px) scaleY(.55)}.cbottom{transform:rotateX(-90deg) translateZ(40px) scaleY(.55)}
.tri-prism{position:absolute;left:50%;top:47%;width:116px;height:95px;transform:translate(-50%,-50%) rotateX(-18deg) rotateY(-25deg);transform-style:preserve-3d;animation:spin 11s linear infinite}.prism-face{position:absolute;inset:0;background:linear-gradient(135deg,#58d5e5ad,#7c69ff90);border:2px solid #c9f5ff;box-shadow:inset 0 0 15px #d5f8ff34}.pf1{clip-path:polygon(0 100%,50% 0,100% 100%);transform:translateZ(25px)}.pf2{clip-path:polygon(0 100%,50% 0,100% 100%);transform:translateZ(-25px) rotateY(180deg)}.pf3{clip-path:polygon(0 100%,50% 0,50% 0,0 100%);transform:rotateY(0deg) translateZ(25px)}.pf4{clip-path:polygon(50% 0,100% 100%,0 100%);transform:rotateY(90deg) translateZ(25px)}.pf5{clip-path:polygon(0 100%,100% 100%,100% 0);transform:rotateY(-90deg) translateZ(25px)}
@keyframes rotate4d{0%{transform:translate(-50%,-50%) rotateY(0deg) rotateX(-8deg)}50%{transform:translate(-50%,-50%) rotateY(180deg) rotateX(24deg)}100%{transform:translate(-50%,-50%) rotateY(360deg) rotateX(-8deg)}}@keyframes rotateInner4d{0%{transform:translate(-50%,-50%) translateZ(8px) rotateX(24deg) rotateY(0deg) rotateZ(0deg)}100%{transform:translate(-50%,-50%) translateZ(8px) rotateX(-24deg) rotateY(180deg) rotateZ(40deg)}}@keyframes spinSphere{to{filter:hue-rotate(25deg);transform:translate(-50%,-50%) rotateY(360deg) rotateX(12deg)}}
.cell{width:146px;height:105px;animation:spin 17s linear infinite}.membrane{position:absolute;left:3px;top:5px;width:140px;height:95px;border:5px solid #8ce5d4;border-radius:50%;background:radial-gradient(ellipse at 35% 28%,#51dbc955,#8a64ec66,#162556aa);box-shadow:0 0 16px #5ddfc688}.cell-nucleus{position:absolute;left:54px;top:32px;width:37px;height:37px;border-radius:50%;background:radial-gradient(circle at 30% 20%,#ffd3fa,#ad52d4 55%,#602a9e);border:2px solid #e2aeff}.organelle{position:absolute;width:22px;height:11px;border-radius:50%;background:linear-gradient(90deg,#ffc16b,#f46c5f);border:1px solid #ffe2ac}.o1{left:20px;top:36px}.o2{left:91px;top:58px;transform:rotate(-25deg)}.o3{left:40px;top:74px;transform:rotate(20deg)}.o4{left:100px;top:24px}
.ramp{position:absolute;left:24%;top:56%;width:58%;height:9px;background:linear-gradient(90deg,#8eeaff,#8f78ff);transform:rotate(-20deg);border-radius:9px;box-shadow:0 0 15px #59bffb}.ball{position:absolute;left:34%;top:29%;width:33px;height:33px;border-radius:50%;background:radial-gradient(circle at 28% 23%,#ffd7a0,#ff923d 48%,#ad3c23);box-shadow:0 0 15px #ff995566;animation:roll 3s ease-in-out infinite}.arrow{position:absolute;color:#b6f6e2;font-size:30px;font-weight:900;text-shadow:0 0 10px #5df8c5}.arrow1{left:58%;top:12%;animation:pulse 1.3s ease-in-out infinite}.arrow2{left:76%;top:42%;color:#ffcc74}
.sun{position:absolute;left:13%;top:14%;width:31px;height:31px;border-radius:50%;background:radial-gradient(circle at 25% 25%,#fff9bd,#ffb84d 65%,#e96739);box-shadow:0 0 24px #ffb84d}.cloud{position:absolute;width:73px;height:27px;border-radius:20px;background:#e3eeff;box-shadow:inset 0 -6px #a3bbed}.cloud:before,.cloud:after{content:'';position:absolute;border-radius:50%;background:#e3eeff}.cloud:before{width:30px;height:30px;top:-14px;left:10px}.cloud:after{width:38px;height:35px;top:-17px;right:7px}.cloud1{left:48%;top:24%;animation:drift 6s linear infinite}.cloud2{left:18%;top:43%;transform:scale(.65);opacity:.9}.drop{position:absolute;width:8px;height:15px;background:linear-gradient(#a9efff,#388df4);border-radius:70% 25% 70% 70%;transform:rotate(25deg);animation:rain 1.4s ease-in infinite}.d1{left:58%;top:42%}.d2{left:68%;top:48%;animation-delay:-.4s}.d3{left:75%;top:40%;animation-delay:-.8s}.ocean{position:absolute;bottom:-19px;left:0;width:100%;height:55px;border-radius:48% 48% 0 0;background:linear-gradient(#21b8e6,#1857b9)}.vapour{position:absolute;left:23%;bottom:23%;width:20px;height:34px;border-left:3px dashed #d5f8ff}
.earth{width:116px;height:116px;border-radius:50%;overflow:hidden;background:radial-gradient(circle at 30% 24%,#a7f8ff 0%,#3899f5 28%,#1750ba 65%,#08184c 100%);box-shadow:inset -18px -7px 22px #030924b8,0 0 17px #5fb5ff88;animation:spin 14s linear infinite}.land{position:absolute;background:linear-gradient(120deg,#7cf1a2,#1c9865);border-radius:48% 30% 55% 45%;box-shadow:inset -3px -4px 5px #0a553f99}.l1{width:36px;height:48px;left:25px;top:20px;transform:rotate(22deg)}.l2{width:25px;height:37px;left:58px;top:57px;transform:rotate(-17deg)}.l3{width:29px;height:21px;left:70px;top:21px;transform:rotate(15deg)}.atmosphere{position:absolute;inset:-4px;border:3px solid #88dfff;border-radius:50%;box-shadow:0 0 13px #5ec9ff}
.magnet{width:136px;height:116px;animation:float 4s ease-in-out infinite}.magnetbody{position:absolute;left:28px;top:22px;width:80px;height:72px;border-left:15px solid #f15f7b;border-right:15px solid #53bcff;border-bottom:15px solid #7b8cff;border-radius:0 0 22px 22px;filter:drop-shadow(0 0 9px #9ad9ff66)}.north,.south{position:absolute;top:9px;width:28px;height:25px;border-radius:7px 7px 2px 2px;display:grid;place-items:center;font-weight:900;color:white}.north{left:29px;background:linear-gradient(#ffabb7,#d42d50)}.south{right:28px;background:linear-gradient(#a1e0ff,#256cc8)}.fieldline{position:absolute;border:1px dashed #83f5dc;border-bottom:0;border-radius:60% 60% 0 0;width:85px;height:58px;left:25px;top:8px;opacity:.6}.f2{transform:scale(.78);top:21px}.f3{transform:scale(1.18);top:-4px;opacity:.3}.volcano{position:absolute;left:50%;top:51%;width:154px;height:132px;transform:translate(-50%,-50%);transform-style:preserve-3d;animation:float 4s ease-in-out infinite}.mountain{position:absolute;left:15px;top:12px;width:0;height:0;border-left:62px solid transparent;border-right:62px solid transparent;border-bottom:107px solid #8d5364;filter:drop-shadow(7px 5px 3px #07102a)}.crater{position:absolute;left:48px;top:16px;width:57px;height:17px;background:radial-gradient(ellipse,#2e1525,#ff7c43 62%,#ffb05a);border-radius:50%;box-shadow:0 0 13px #ff733e}.lavaflow{position:absolute;left:82px;top:30px;width:13px;height:82px;background:linear-gradient(#ffcc70,#ff6839,#a52b41);transform:rotate(-16deg);border-radius:40%;box-shadow:0 0 9px #ff6839}.rocklayer{position:absolute;left:26px;width:104px;height:13px;border-radius:50%;background:#cd8b71;opacity:.8}.layer1{top:83px}.layer2{top:101px;background:#66476e}.flower{position:absolute;left:50%;top:50%;width:140px;height:142px;transform:translate(-50%,-50%);transform-style:preserve-3d;animation:spin 16s linear infinite}.stem{position:absolute;left:67px;top:65px;width:8px;height:72px;border-radius:9px;background:linear-gradient(90deg,#0e815b,#68dfa2)}.leaf{position:absolute;top:91px;width:35px;height:19px;background:linear-gradient(90deg,#31b879,#b3ffb7);border-radius:100% 0 100% 0}.leaf1{left:33px;transform:rotate(12deg)}.leaf2{left:72px;top:105px;transform:rotate(185deg)}.petal{position:absolute;left:52px;top:18px;width:29px;height:47px;background:radial-gradient(circle at 30% 20%,#fff0a7,#fb83ca 65%,#9b4fb9);border-radius:60% 60% 45% 45%;transform-origin:50% 42px;box-shadow:0 0 8px #ff86d355}.p1{transform:rotate(0deg)}.p2{transform:rotate(60deg)}.p3{transform:rotate(120deg)}.p4{transform:rotate(180deg)}.p5{transform:rotate(240deg)}.p6{transform:rotate(300deg)}.flowercenter{position:absolute;left:53px;top:43px;width:28px;height:28px;border-radius:50%;background:radial-gradient(circle at 30% 25%,#fff2a5,#d98725 65%,#8d4a17);border:2px solid #ffd975}
.book{position:absolute;left:50%;top:50%;width:150px;height:105px;transform:translate(-50%,-50%);transform-style:preserve-3d;animation:float 4s ease-in-out infinite}.book-cover{position:absolute;left:9px;top:7px;width:132px;height:91px;border-radius:8px;background:linear-gradient(145deg,#a78bfa,#4f46e5 58%,#222e77);border:2px solid #ddd6fe;box-shadow:inset -8px -5px 15px #11194b66,0 10px 18px #02061777}.page-left,.page-right{position:absolute;top:15px;width:61px;height:75px;background:linear-gradient(90deg,#fff8dc,#e5e7eb);border:1px solid #bdc6db;border-radius:5px}.page-left{left:16px;transform:rotateY(9deg)}.page-right{right:16px;transform:rotateY(-9deg)}.spine{position:absolute;left:73px;top:8px;width:8px;height:89px;background:linear-gradient(90deg,#372c8d,#b4a7ff,#4338a8);border-radius:3px}.bookmark{position:absolute;right:25px;top:5px;width:12px;height:32px;background:#f472b6;clip-path:polygon(0 0,100% 0,100% 100%,50% 75%,0 100%)}.book-title{position:absolute;left:40px;top:41px;color:#3f2e98;font-size:9px;letter-spacing:2px;font-weight:900;transform:rotate(-4deg)}.stage.exploded .book-cover{transform:translateZ(30px) translateY(-7px) scale(1.05)}.stage.exploded .page-left{transform:translate(-13px,0) translateZ(35px) rotateY(15deg)}.stage.exploded .page-right{transform:translate(13px,0) translateZ(35px) rotateY(-15deg)}.stage.exploded .spine{transform:translateZ(38px) scaleY(1.08)}.stage.exploded .bookmark{transform:translateY(-12px) translateZ(32px)}.stage.exploded .book-title{transform:translateY(-15px) translateZ(38px) rotate(-4deg)}
.circuit{width:140px;height:96px}.wire{position:absolute;background:#88c9ff;box-shadow:0 0 9px #77e3ff}.wire.top{left:20px;top:5px;width:100px;height:4px}.wire.right{right:17px;top:5px;width:4px;height:84px}.wire.bottom{left:20px;bottom:7px;width:100px;height:4px}.wire.left{left:17px;top:5px;width:4px;height:84px}.battery{position:absolute;left:10px;top:34px;width:18px;height:30px;background:linear-gradient(90deg,#e5efff,#8fa6d6);border:2px solid #fff;border-radius:3px}.lamp{position:absolute;right:7px;top:31px;width:24px;height:24px;border-radius:50%;background:radial-gradient(circle,#fff8bd,#ffd44c 54%,#ac7721);box-shadow:0 0 18px #ffd44c}.electron{position:absolute;width:9px;height:9px;border-radius:50%;background:#b8fdff;box-shadow:0 0 12px #70fff6;animation:electron 2.5s linear infinite}.e1{left:40px;top:1px}.e2{left:116px;top:63px;animation-delay:-.8s}.e3{left:57px;bottom:2px;animation-delay:-1.6s}
@keyframes spin{to{transform:translate(-50%,-50%) rotateY(360deg) rotateX(22deg)}}@keyframes float{0%,100%{margin-top:0}50%{margin-top:-8px}}@keyframes electron{0%{transform:rotate(0deg) translateX(-42px)}100%{transform:rotate(360deg) translateX(-42px)}}@keyframes roll{0%,100%{left:30%;transform:rotate(0)}50%{left:63%;transform:rotate(320deg)}}@keyframes pulse{50%{transform:scale(1.2);opacity:.6}}@keyframes drift{0%{margin-left:-10px}50%{margin-left:15px}100%{margin-left:-10px}}@keyframes rain{0%{transform:translateY(-5px) rotate(25deg);opacity:0}35%{opacity:1}100%{transform:translateY(50px) rotate(25deg);opacity:0}}
</style></head><body><div class="wrap"><div class="title">${title}</div><div class="stage" id="stage"><div class="star"></div><div class="model-root" id="model-root">${shape}</div></div>
<div class="caption">${description} Drag with one finger to rotate; pinch with two fingers to zoom. Tap model parts to discover details and hear gentle sound cues.</div>
<div class="controls">
<button id="explode" aria-pressed="false">🧩 Explode parts</button>
<button id="turn360">⟳ 360°</button>
<button id="reset-view">Reset</button>
</div>
${chemistryControls}
<div id="detail" class="detail"><strong>4D Explorer ready</strong><br>Tap an object, or use Explode parts to inspect its components.</div></div>
<script>(function(){
const stage=document.getElementById('stage'),detail=document.getElementById('detail');
const detailMap={
'.nucleus':['Nucleus','The central region of an atom contains protons and neutrons.'],
'.orbit':['Electron region','Electrons occupy quantum orbitals; this orbit is a simplified teaching illustration.'],
'.orbit b':['Electron','Electrons have a negative electric charge.'],
'.oxygen':['Oxygen atom','The red sphere represents oxygen in this water molecule model.'],
'.hydrogen':['Hydrogen atom','Water contains two hydrogen atoms bonded to one oxygen atom.'],
'.bond':['Covalent bond','Atoms share electron pairs to form a covalent bond.'],
'.face.front':['Front face','This is one of the six square faces of a cube.'],
'.face.back':['Back face','The hidden opposite face is also a square.'],
'.face.right':['Right face','A cube has six equal square faces and twelve edges.'],
'.face.left':['Left face','Edges meet at vertices; a cube has eight vertices.'],
'.face.top':['Top face','Rotate the cube to compare all six faces.'],
'.face.bottom':['Bottom face','The bottom face is equal in size to the other cube faces.'],
'.hyper-face':['Tesseract outer cube face','One face of the outer cube in a projected tesseract. Eight connecting edges join this cube to the inner cube.'],
'.inner-face':['Tesseract inner cube face','This is a second cube in the 3D projection of a 4D hypercube. It moves on a second rotation axis.'],
'.hyper-link':['Tesseract connecting edge','This line connects a corresponding vertex of the projected inner and outer cubes. Eight such connections complete the tesseract wireframe.'],
'.solid-sphere':['Sphere','A sphere has a continuous curved surface and no flat faces, straight edges or vertices.'],
'.solid-cylinder':['Cylinder curved side','A cylinder has one curved side and two circular ends.'],
'.cylinder-top':['Cylinder top circle','This circular end is parallel and equal to the bottom circular end.'],
'.solid-cone':['Cone','A cone tapers from its circular base to one apex.'],
'.cone-base':['Cone circular base','This is the flat circular base of the cone.'],
'.cuboid-face':['Cuboid face','A cuboid has six rectangular faces, twelve edges and eight vertices.'],
'.prism-face':['Triangular prism face','A triangular prism has two triangular ends joined by three rectangular faces.'],
'.membrane':['Cell membrane','The membrane controls movement of substances into and out of the cell.'],
'.cell-nucleus':['Cell nucleus','The nucleus contains most of an animal cell’s DNA.'],
'.organelle':['Organelle','Organelles perform specialised jobs inside a cell.'],
'.ball':['Moving ball','An unbalanced force changes an object’s speed or direction.'],
'.ramp':['Ramp','The ramp provides an inclined surface; its slope affects the motion.'],
'.arrow1':['Force arrow','An arrow shows the direction of a force or motion in this illustration.'],
'.arrow2':['Upward arrow','Forces have both magnitude and direction.'],
'.sun':['Sunlight','Solar energy helps drive evaporation in the water cycle.'],
'.cloud':['Cloud','Cooling water vapour condenses into tiny droplets or ice crystals.'],
'.drop':['Precipitation','Water falls from clouds as rain, snow or other precipitation.'],
'.ocean':['Collection','Water gathers in oceans, lakes and other bodies after precipitation.'],
'.earth':['Earth','Earth rotates on its axis, producing the day-and-night cycle.'],
'.land':['Land','Continents and islands are parts of Earth’s solid surface.'],
'.atmosphere':['Atmosphere','Earth’s atmosphere contains the gases that surround the planet.'],
'.battery':['Battery','A battery supplies electrical energy to a simple circuit.'],
'.lamp':['Lamp','Current through the lamp can transfer electrical energy into light and heat.'],
'.wire':['Wire','A conducting path connects circuit components.'],
'.electron':['Charge marker','The moving dots are illustrative markers, not a scale drawing of individual electrons.'],
'.magnetbody':['Horseshoe magnet','A magnet creates a field around it; the red and blue ends mark opposite poles.'],
'.north':['North pole','The marked north pole is opposite to the south pole.'],
'.south':['South pole','Opposite magnetic poles attract; like poles repel.'],
'.fieldline':['Magnetic field illustration','The dotted curves are a simplified representation of the magnetic field pattern.'],
'.mountain':['Volcanic cone','A cone can form from cooled lava, ash and other volcanic material.'],
'.crater':['Crater','The crater is the opening around a volcanic vent.'],
'.lavaflow':['Lava flow','Molten rock that reaches Earth’s surface is called lava.'],
'.rocklayer':['Rock layer','Rock and ash can build up in layers during eruptions.'],
'.petal':['Petal','Petals can help attract pollinators such as insects.'],
'.flowercenter':['Flower centre','This stylised centre represents the flower’s reproductive structures.'],
'.stem':['Stem','The stem supports the plant and transports water and sugars.'],
'.leaf':['Leaf','Leaves are major sites of photosynthesis in many plants.'],
'.book-cover':['Book cover','The cover protects the pages and identifies the learning resource.'],
'.page-left':['Left page','Pages hold text, pictures and learning activities.'],
'.page-right':['Right page','Touch the page to reveal facts from this 4D learning book.'],
'.spine':['Book spine','The spine holds the book together along its bound edge.'],
'.bookmark':['Bookmark','A bookmark helps a reader find a place again.'],
'.book-title':['Learning topic','This model represents an interactive subject guide with questions and explanations.']
};
Object.keys(detailMap).forEach(selector=>stage.querySelectorAll(selector).forEach(el=>{
  el.style.cursor='pointer';el.setAttribute('role','button');el.setAttribute('tabindex','0');
  el.setAttribute('aria-label',detailMap[selector][0]);
  const show=()=>{detail.innerHTML='<strong>'+detailMap[selector][0]+'</strong><br>'+detailMap[selector][1];};
  el.addEventListener('click',e=>{e.stopPropagation();show()});
  el.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();show()}});
}));
const modelRoot=document.getElementById('model-root');
let down=false,lastX=0,lastY=0,angle=0,pitch=0,zoom=1,dragged=false;
const activePointers=new Map();let pinchStartDistance=0,pinchStartZoom=1;
function playCue(kind){try{const AC=window.AudioContext||window.webkitAudioContext;if(!AC)return;const ac=playCue.ac||(playCue.ac=new AC());if(ac.state==='suspended')ac.resume();const osc=ac.createOscillator(),gain=ac.createGain();osc.type=kind==='correct'?'sine':'triangle';osc.frequency.value=kind==='correct'?880:kind==='tap'?620:390;gain.gain.setValueAtTime(.0001,ac.currentTime);gain.gain.exponentialRampToValueAtTime(.075,ac.currentTime+.015);gain.gain.exponentialRampToValueAtTime(.0001,ac.currentTime+.16);osc.connect(gain);gain.connect(ac.destination);osc.start();osc.stop(ac.currentTime+.17);}catch(_){}}
function applyView(smooth){modelRoot.style.transition=smooth?'transform 450ms cubic-bezier(.2,.72,.2,1)':'transform 70ms linear';modelRoot.style.transform='scale('+zoom+') rotateX('+pitch+'deg) rotateY('+angle+'deg)';}
stage.addEventListener('pointerdown',e=>{activePointers.set(e.pointerId,{x:e.clientX,y:e.clientY});stage.setPointerCapture(e.pointerId);if(activePointers.size===2){const pts=[...activePointers.values()];pinchStartDistance=Math.hypot(pts[0].x-pts[1].x,pts[0].y-pts[1].y);pinchStartZoom=zoom;down=false;return;}down=true;dragged=false;lastX=e.clientX;lastY=e.clientY;});
stage.addEventListener('pointermove',e=>{if(activePointers.has(e.pointerId))activePointers.set(e.pointerId,{x:e.clientX,y:e.clientY});if(activePointers.size>=2){const pts=[...activePointers.values()];const d=Math.max(1,Math.hypot(pts[0].x-pts[1].x,pts[0].y-pts[1].y));zoom=Math.max(.55,Math.min(2.2,+(pinchStartZoom*d/Math.max(1,pinchStartDistance)).toFixed(2)));applyView(false);return;}if(!down)return;const dx=e.clientX-lastX,dy=e.clientY-lastY;if(Math.abs(dx)+Math.abs(dy)>2)dragged=true;angle+=dx;pitch=Math.max(-65,Math.min(65,pitch-dy*.65));lastX=e.clientX;lastY=e.clientY;stage.style.perspectiveOrigin=(50+angle*.05)+'% 50%';applyView(false)});
function endPointer(e){activePointers.delete(e.pointerId);if(activePointers.size<2)pinchStartDistance=0;if(activePointers.size===0)down=false;}
stage.addEventListener('pointerup',endPointer);stage.addEventListener('pointercancel',endPointer);
stage.querySelectorAll('[data-part],.component,[tabindex="0"]').forEach(el=>el.addEventListener('click',()=>playCue('tap')));
document.getElementById('explode').addEventListener('click',function(){const state=stage.classList.toggle('exploded');this.setAttribute('aria-pressed',String(state));this.textContent=state?'🧩 Reassemble parts':'🧩 Explode parts';playCue('tap');detail.innerHTML=state?'<strong>Exploded view</strong><br>Components are separated for inspection. Tap each component for its name and explanation.':'<strong>Assembled view</strong><br>Components are back in their normal positions.';});
document.getElementById('turn360').addEventListener('click',()=>{angle+=360;applyView(true);playCue('tap');detail.innerHTML='<strong>360° view</strong><br>Rotate vertically by dragging up/down, or drag sideways to inspect every side.'});
document.getElementById('reset-view').addEventListener('click',()=>{angle=0;pitch=0;zoom=1;stage.classList.remove('exploded');playCue('tap');document.getElementById('explode').setAttribute('aria-pressed','false');document.getElementById('explode').textContent='🧩 Explode parts';stage.style.perspectiveOrigin='50% 50%';applyView(true);detail.innerHTML='<strong>3D Explorer ready</strong><br>Tap a component to learn about it, then try zoom and exploded view.'});
const chemistryLab=document.getElementById('chemistry-lab');
if(chemistryLab){
  const note=document.querySelector('.reaction-note');
  const labNote=(heading,message)=>{detail.innerHTML='<strong>'+heading+'</strong><br>'+message;playCue('tap');};
  document.getElementById('mix-indicator').addEventListener('click',()=>{
    chemistryLab.classList.remove('gas-reaction');chemistryLab.classList.add('indicator-reaction');
    chemistryLab.querySelector('.label-a').textContent='Indicator';chemistryLab.querySelector('.label-b').textContent='Solution';
    chemistryLab.querySelector('.liquid-result').style.background='linear-gradient(180deg,#ff88dc,#9d48f0 72%,#532ba0)';
    labNote('Colour-change reaction','The simulated indicator changes colour in the solution. The result is a visual clue about the solution’s properties.');
    if(note)note.textContent='Simulation only: indicator colour changes are illustrative, not a real test result.';
  });
  document.getElementById('mix-gas').addEventListener('click',()=>{
    chemistryLab.classList.remove('indicator-reaction');chemistryLab.classList.add('gas-reaction');
    chemistryLab.querySelector('.label-a').textContent='Reactant A';chemistryLab.querySelector('.label-b').textContent='Reactant B';
    chemistryLab.querySelector('.liquid-result').style.background='linear-gradient(180deg,#c4f8ff,#30a9d2 72%,#175b95)';
    labNote('Gas-producing reaction','Bubbles rise from the virtual mixture, representing a gas being produced by a simulated reaction.');
    if(note)note.textContent='No real chemicals are mixed. The bubbles and colour change are computer-generated.';
  });
  document.getElementById('reset-beakers').addEventListener('click',()=>{
    chemistryLab.classList.remove('indicator-reaction','gas-reaction');
    chemistryLab.querySelector('.label-a').textContent='A';chemistryLab.querySelector('.label-b').textContent='B';
    chemistryLab.querySelector('.liquid-result').style.background='';
    labNote('Virtual chemistry lab reset','Choose a reaction to see the simulated result.');
    if(note)note.textContent='Virtual chemicals only • No real chemicals are mixed.';
  });
}
})();</script>
</body></html>
""".trimIndent()
}
