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
    SolarSystemWebView(quizMode = false, height = 260.dp)
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
canvas{width:100%;height:100%;display:block;touch-action:manipulation}
.ring{position:absolute;width:30px;height:30px;border:2px solid #ffe783;border-radius:50%;box-shadow:0 0 12px #ffe783;transform:translate(-50%,-50%);pointer-events:none;display:none}
.caption{font-size:12px;color:#b8c7f9;margin:7px 2px 0}
.panel{margin-top:9px;padding:12px;border-radius:15px;background:rgba(26,36,80,.92);border:1px solid #334271}
.eyebrow{font-size:11px;color:#9db2f4;text-transform:uppercase;letter-spacing:.09em;font-weight:800}
h3{font-size:17px;margin:5px 0 8px;line-height:1.3}
p{font-size:13px;color:#d6defb;line-height:1.4;margin:5px 0 9px}
input{width:100%;height:42px;border-radius:11px;border:1px solid #6375b2;background:#080e2b;color:white;padding:0 12px;font-size:16px;outline:none}
button{border:0;border-radius:12px;background:linear-gradient(100deg,#5848ef,#3a70ed);color:white;font-weight:750;font-size:14px;padding:12px 14px;min-height:42px;width:100%;margin-top:8px}
button:disabled{opacity:.4}
.status{min-height:23px;font-size:12px;color:#b9f2df;margin-top:7px;line-height:1.4}
.score{font-size:12px;color:#c4d3ff;text-align:right}
@media(min-width:520px){.scene{height:${if (quizMode) "360px" else "250px"}}}
</style>
</head>
<body>
<div class="shell">
  <div class="scene">
    <canvas id="scene" aria-label="Interactive 3D style solar system"></canvas>
    <div class="ring" id="selectionRing"></div>
  </div>
  <div class="caption" id="sceneCaption">A live, interactive planetary model. Tap a planet to select it.</div>
  <div class="panel" id="game" style="display:${if (quizMode) "block" else "none"}">
    <div class="eyebrow" id="round">Planet challenge</div>
    <h3 id="clue">Find the planet</h3>
    <p>Tap the planet described in the clue, then type its name. Planet labels are hidden so you can test your knowledge.</p>
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
let aspectScale=1,lastPositions=[],sphereProgram=null,orbitProgram=null,sphereBuffer=null,sphereIndexBuffer=null,ringBuffer=null,orbitBuffers=[],indexCount=0;
function compile(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s)||'shader error');return s;}
function makeProgram(vs,fs){const p=gl.createProgram();gl.attachShader(p,compile(gl.VERTEX_SHADER,vs));gl.attachShader(p,compile(gl.FRAGMENT_SHADER,fs));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(p)||'link error');return p;}
const planetVS='attribute vec3 aPosition;attribute vec3 aNormal;uniform vec3 uCenter;uniform float uRadius;uniform float uRotation;uniform float uAspectScale;varying vec3 vNormal;varying vec3 vWorld;void main(){float c=cos(uRotation),s=sin(uRotation);vec3 p=vec3(aPosition.x*c+aPosition.z*s,aPosition.y,-aPosition.x*s+aPosition.z*c);vec3 world=uCenter+p*uRadius;float depth=max(2.0,8.0-world.z);gl_Position=vec4(world.x*1.65*uAspectScale/depth,world.y*2.0/depth,-world.z/9.0,1.0);vNormal=normalize(vec3(aNormal.x*c+aNormal.z*s,aNormal.y,-aNormal.x*s+aNormal.z*c));vWorld=world;}';
const planetFS='precision mediump float;uniform vec3 uColor;uniform float uSun;varying vec3 vNormal;varying vec3 vWorld;void main(){vec3 N=normalize(vNormal);vec3 L=normalize(vec3(-vWorld.x,-vWorld.y,4.5-vWorld.z));float d=max(dot(N,L),0.0);float rim=pow(1.0-max(dot(N,vec3(0.0,0.0,1.0)),0.0),2.0);vec3 c=uColor*(0.22+0.86*d)+uColor*uSun*0.48+vec3(0.05,0.10,0.24)*rim*0.3;gl_FragColor=vec4(c,1.0);}';
const lineVS='attribute vec3 aPosition;uniform float uAspectScale;void main(){float d=max(2.0,8.0-aPosition.z);gl_Position=vec4(aPosition.x*1.65*uAspectScale/d,aPosition.y*2.0/d,-aPosition.z/9.0,1.0);}';
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
function planetPosition(p,t){const a=t*0.00012/p.speed+p.phase;return {x:Math.cos(a)*p.orbit,y:Math.sin(a)*p.orbit*0.31,z:Math.sin(a)*p.orbit*0.73};}
function project(pos){const rect=canvas.getBoundingClientRect(),d=Math.max(2,8-pos.z),nx=pos.x*1.65*aspectScale/d,ny=pos.y*2/d;return {x:(nx+1)*rect.width/2,y:(1-ny)*rect.height/2};}
function drawPlanet(pos,r,col,rot,isSun){gl.useProgram(sphereProgram);gl.bindBuffer(gl.ARRAY_BUFFER,sphereBuffer);const ap=gl.getAttribLocation(sphereProgram,'aPosition'),an=gl.getAttribLocation(sphereProgram,'aNormal');gl.enableVertexAttribArray(ap);gl.vertexAttribPointer(ap,3,gl.FLOAT,false,24,0);gl.enableVertexAttribArray(an);gl.vertexAttribPointer(an,3,gl.FLOAT,false,24,12);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,sphereIndexBuffer);gl.uniform3f(gl.getUniformLocation(sphereProgram,'uCenter'),pos.x,pos.y,pos.z);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uRadius'),r);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uRotation'),rot);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uAspectScale'),aspectScale);gl.uniform3f(gl.getUniformLocation(sphereProgram,'uColor'),col[0],col[1],col[2]);gl.uniform1f(gl.getUniformLocation(sphereProgram,'uSun'),isSun?1:0);gl.drawElements(gl.TRIANGLES,indexCount,gl.UNSIGNED_SHORT,0);}
function drawLines(buffer,count,col){gl.useProgram(orbitProgram);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);const a=gl.getAttribLocation(orbitProgram,'aPosition');gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,3,gl.FLOAT,false,0,0);gl.uniform1f(gl.getUniformLocation(orbitProgram,'uAspectScale'),aspectScale);gl.uniform4f(gl.getUniformLocation(orbitProgram,'uLineColor'),col[0],col[1],col[2],col[3]);gl.drawArrays(gl.LINE_LOOP,0,count);}
function drawGL(t){gl.clearColor(0.015,0.024,0.075,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.depthMask(false);orbitBuffers.forEach(b=>drawLines(b,160,[0.28,0.39,0.72,0.48]));const sat=planetPosition(planets[5],t),rp=[];for(let i=0;i<100;i++){const a=i*Math.PI*2/100;rp.push(sat.x+Math.cos(a)*0.31,sat.y+Math.sin(a)*0.07,sat.z+Math.sin(a)*0.16);}gl.bindBuffer(gl.ARRAY_BUFFER,ringBuffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(rp),gl.DYNAMIC_DRAW);drawLines(ringBuffer,100,[0.93,0.78,0.47,0.95]);gl.depthMask(true);drawPlanet({x:0,y:0,z:0},0.43,[1.0,0.57,0.12],t*0.0001,true);lastPositions=[];planets.forEach(p=>{const pos=planetPosition(p,t);lastPositions.push({name:p.name,pos:pos,screen:project(pos),radius:p.radius});drawPlanet(pos,p.radius,p.color,t*0.0007/p.speed,false);});}
function drawFallback(t){if(!ctx)return;const w=canvas.width,h=canvas.height,cx=w/2,cy=h/2,scale=Math.min(w/7.4,h/3.7);ctx.clearRect(0,0,w,h);ctx.fillStyle='#05091f';ctx.fillRect(0,0,w,h);ctx.strokeStyle='rgba(107,133,216,.35)';ctx.lineWidth=1;planets.forEach(p=>{ctx.beginPath();ctx.ellipse(cx,cy,p.orbit*scale,p.orbit*scale*.34,0,0,Math.PI*2);ctx.stroke();});const sg=ctx.createRadialGradient(cx-6,cy-6,2,cx,cy,scale*.43);sg.addColorStop(0,'#fff5a6');sg.addColorStop(.5,'#ffab36');sg.addColorStop(1,'#e54b0c');ctx.fillStyle=sg;ctx.beginPath();ctx.arc(cx,cy,scale*.43,0,Math.PI*2);ctx.fill();lastPositions=[];planets.forEach(p=>{const pos=planetPosition(p,t),x=cx+pos.x*scale,y=cy+pos.y*scale;lastPositions.push({name:p.name,pos:pos,screen:{x:x/(window.devicePixelRatio||1),y:y/(window.devicePixelRatio||1)},radius:p.radius});const r=Math.max(4,p.radius*scale),col='rgb('+Math.round(p.color[0]*255)+','+Math.round(p.color[1]*255)+','+Math.round(p.color[2]*255)+')';const g=ctx.createRadialGradient(x-r*.4,y-r*.4,1,x,y,r);g.addColorStop(0,'#ffffff');g.addColorStop(.24,col);g.addColorStop(1,'#080d25');ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();if(p.name==='Saturn'){ctx.strokeStyle='#e2cf99';ctx.beginPath();ctx.ellipse(x,y,r*1.8,r*.55,-.24,0,Math.PI*2);ctx.stroke();}});}
function frame(t){resize();if(gl)drawGL(t);else drawFallback(t);if(selected){const p=lastPositions.find(x=>x.name===selected);if(p){const rect=canvas.getBoundingClientRect();ring.style.display='block';ring.style.left=p.screen.x+'px';ring.style.top=p.screen.y+'px';ring.style.width=Math.max(28,p.radius*rect.width*.16)+'px';ring.style.height=ring.style.width;}else ring.style.display='none';}requestAnimationFrame(frame);}
canvas.addEventListener('click',function(e){const rect=canvas.getBoundingClientRect(),x=e.clientX-rect.left,y=e.clientY-rect.top;let nearest=null,best=Infinity;lastPositions.forEach(p=>{const dx=p.screen.x-x,dy=p.screen.y-y,d=Math.sqrt(dx*dx+dy*dy);if(d<best){best=d;nearest=p;}});if(nearest&&best<Math.max(24,nearest.radius*rect.width*.15)){selected=nearest.name;caption.textContent='Planet selected. Use its colour, size and orbit to work out the name.';if(QUIZ_MODE&&!answered)statusEl.textContent='Planet selected — now type your guess.';}});
function normalize(s){return String(s||'').toLowerCase().replace(/[^a-z]/g,'');}
function updateScore(){scoreEl.textContent='Score: '+correct+' / '+total;}
function nextQuestion(){if(asked.length>=planets.length){complete=true;clueEl.textContent='Solar System Explorer complete!';roundEl.textContent='Challenge complete';statusEl.textContent='You scored '+correct+' out of '+total+'. Play again to practise all eight planets.';checkBtn.style.display='none';nextBtn.textContent='Play again';nextBtn.style.display='block';guessEl.disabled=true;updateScore();return;}const remaining=planets.filter(p=>!asked.includes(p.name));target=remaining[Math.floor(Math.random()*remaining.length)];asked.push(target.name);selected=null;answered=false;guessEl.value='';guessEl.disabled=false;checkBtn.disabled=false;checkBtn.style.display='block';nextBtn.style.display='none';ring.style.display='none';roundEl.textContent='Planet challenge '+asked.length+' of '+planets.length;clueEl.textContent=target.clue;statusEl.textContent='Tap the matching planet in the scene, then type its name.';caption.textContent='Tap a planet to select it. Planet names are hidden.';updateScore();}
if(QUIZ_MODE){nextQuestion();checkBtn.addEventListener('click',function(){if(answered)return;const guess=normalize(guessEl.value);if(!selected||!guess){statusEl.textContent='Tap a planet and type its name before checking.';return;}answered=true;total++;const ok=selected===target.name&&guess===normalize(target.name);if(ok){correct++;statusEl.textContent='Correct! '+target.name+' is a great discovery.';}else{statusEl.textContent='Good try. The answer was '+target.name+'. Keep exploring!';}guessEl.disabled=true;checkBtn.disabled=true;nextBtn.style.display='block';nextBtn.textContent=asked.length>=planets.length?'See final score':'Next planet';updateScore();});nextBtn.addEventListener('click',function(){if(complete){asked=[];correct=0;total=0;complete=false;nextQuestion();}else nextQuestion();});}else{document.getElementById('game').style.display='none';caption.textContent='Explore the moving solar system. Tap a planet to highlight it.';}
requestAnimationFrame(frame);
})();
</script>
</body>
</html>
""".trimIndent()
