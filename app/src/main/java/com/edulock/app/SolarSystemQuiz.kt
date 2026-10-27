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
canvas.addEventListener('click',function(e){const rect=canvas.getBoundingClientRect(),x=e.clientX-rect.left,y=e.clientY-rect.top;let nearest=null,best=Infinity;lastPositions.forEach(p=>{const dx=p.screen.x-x,dy=p.screen.y-y,d=Math.sqrt(dx*dx+dy*dy);if(d<best){best=d;nearest=p;}});if(nearest&&best<Math.max(24,nearest.radius*rect.width*.15)){selected=nearest.name;const facts={Mercury:'Smallest planet; its short year lasts 88 Earth days.',Venus:'A thick carbon-dioxide atmosphere makes Venus the hottest planet.',Earth:'Liquid-water oceans and a protective atmosphere support known life.',Mars:'Iron-rich dust gives Mars its rusty red colour.',Jupiter:'Largest planet; its Great Red Spot is a giant storm.',Saturn:'Its spectacular rings are made mostly of ice and rocky particles.',Uranus:'An ice giant that rotates on its side.',Neptune:'A distant ice giant with very fast winds.'};caption.textContent=nearest.name+' — '+(facts[nearest.name]||'Our star supplies light and energy to the Solar System.');if(QUIZ_MODE&&!answered)statusEl.textContent='Selected '+nearest.name+'. Read its facts, then type the planet name.';}});
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


@Composable
fun Learning3DPreview(modelType: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(230.dp),
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
    val model = modelType.lowercase().filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "geometry" }
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
            title = "3D geometry lab"; description = "Rotate the cube to inspect its faces, edges and vertices."
            shape = """<div class="cube"><i class="face front"></i><i class="face back"></i><i class="face right"></i><i class="face left"></i><i class="face top"></i><i class="face bottom"></i></div><div class="shape-label">CUBE · 6 FACES</div>"""
        }
        "cell" -> {
            title = "Cell explorer"; description = "Illustrative animal cell with membrane, nucleus and organelles."
            shape = """<div class="cell"><b class="membrane"></b><b class="cell-nucleus"></b><i class="organelle o1"></i><i class="organelle o2"></i><i class="organelle o3"></i><i class="organelle o4"></i></div>"""
        }
        "force" -> {
            title = "Forces and motion"; description = "A moving ball and arrows illustrate motion and force."
            shape = """<div class="ramp"></div><div class="ball"></div><div class="arrow arrow1">➜</div><div class="arrow arrow2">⬆</div>"""
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
        else -> {
            title = "3D learning model"; description = "Touch and drag the model; use its shapes to answer the question."
            shape = """<div class="cube"><i class="face front"></i><i class="face back"></i><i class="face right"></i><i class="face left"></i><i class="face top"></i><i class="face bottom"></i></div>"""
        }
    }
    return """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;background:#070c22;color:#f5f7ff;font-family:system-ui,sans-serif}
.wrap{padding:8px}.stage{height:178px;position:relative;overflow:hidden;border:1px solid #344476;border-radius:16px;background:radial-gradient(ellipse at 50% 40%,#283d77,#0a112f 72%);perspective:700px;touch-action:pan-y}
.star{position:absolute;width:2px;height:2px;border-radius:50%;background:#dce7ff;box-shadow:30px 26px #dce7ff,72px 93px #9db4f5,145px 20px #fff,210px 120px #dce7ff,260px 46px #8faaff,330px 85px #fff,385px 27px #dce7ff,410px 135px #b5c5ff}
.caption{padding:7px 3px 2px;color:#bfd0ff;font-size:12px;line-height:1.4}.title{font-size:14px;font-weight:800;color:#f7f4ff}.detail{margin-top:7px;min-height:48px;padding:9px 11px;background:linear-gradient(135deg,#172650,#142c48);border:1px solid #3a6191;border-radius:12px;color:#dceaff;font-size:12px;line-height:1.45}.detail strong{color:#82f0db;font-size:13px}
.atom,.molecule,.cube,.cell,.earth,.circuit{position:absolute;left:50%;top:49%;transform:translate(-50%,-50%);transform-style:preserve-3d}
.atom{width:128px;height:110px;animation:float 4s ease-in-out infinite}.nucleus{position:absolute;left:54px;top:44px;width:25px;height:25px;border-radius:50%;background:radial-gradient(circle at 30% 25%,#ffc2ba,#e64756 42%,#67184f);box-shadow:0 0 19px #f05c9b}
.orbit{position:absolute;left:9px;top:24px;width:110px;height:60px;border:2px solid #65d9ff;border-radius:50%;transform-style:preserve-3d}.orbit1{transform:rotateX(65deg) rotateZ(22deg)}.orbit2{transform:rotateX(65deg) rotateZ(82deg)}.orbit3{transform:rotateX(65deg) rotateZ(143deg)}.orbit b{position:absolute;left:90%;top:18%;width:12px;height:12px;background:radial-gradient(circle at 30% 20%,white,#46c8ff 55%,#1e53a8);border-radius:50%;box-shadow:0 0 12px #42d6ff;animation:electron 2s linear infinite}.orbit2 b{animation-delay:-.65s}.orbit3 b{animation-delay:-1.2s}
.molecule{width:175px;height:118px;animation:float 4s ease-in-out infinite}.sphere{position:absolute;border-radius:50%;box-shadow:inset -10px -10px 15px #02061766,0 0 16px #b6ccff44}.oxygen{width:56px;height:56px;left:60px;top:28px;background:radial-gradient(circle at 28% 22%,#fff0ed,#f14f57 48%,#8d193b)}.hydrogen{width:35px;height:35px;background:radial-gradient(circle at 28% 20%,white,#d8e2ed 55%,#61718d)}.h1{left:22px;top:66px}.h2{left:119px;top:66px}.bond{position:absolute;width:58px;height:9px;background:linear-gradient(#c8e2fc,#4a6d98);top:70px;border-radius:9px;transform-style:preserve-3d}.b1{left:34px;transform:rotate(36deg)}.b2{left:91px;transform:rotate(-36deg)}
.cube{width:94px;height:94px;animation:spin 9s linear infinite}.face{position:absolute;width:94px;height:94px;border:2px solid #e6edff;background:linear-gradient(135deg,#7a64ffb8,#24c6dc73);box-shadow:inset 0 0 25px #d9e4ff30}.front{transform:translateZ(47px)}.back{transform:rotateY(180deg) translateZ(47px)}.right{transform:rotateY(90deg) translateZ(47px)}.left{transform:rotateY(-90deg) translateZ(47px)}.top{transform:rotateX(90deg) translateZ(47px)}.bottom{transform:rotateX(-90deg) translateZ(47px)}.shape-label{position:absolute;top:83%;left:50%;transform:translateX(-50%);font-size:10px;font-weight:900;letter-spacing:.12em;color:#c3d5ff}
.cell{width:146px;height:105px;animation:spin 17s linear infinite}.membrane{position:absolute;left:3px;top:5px;width:140px;height:95px;border:5px solid #8ce5d4;border-radius:50%;background:radial-gradient(ellipse at 35% 28%,#51dbc955,#8a64ec66,#162556aa);box-shadow:0 0 16px #5ddfc688}.cell-nucleus{position:absolute;left:54px;top:32px;width:37px;height:37px;border-radius:50%;background:radial-gradient(circle at 30% 20%,#ffd3fa,#ad52d4 55%,#602a9e);border:2px solid #e2aeff}.organelle{position:absolute;width:22px;height:11px;border-radius:50%;background:linear-gradient(90deg,#ffc16b,#f46c5f);border:1px solid #ffe2ac}.o1{left:20px;top:36px}.o2{left:91px;top:58px;transform:rotate(-25deg)}.o3{left:40px;top:74px;transform:rotate(20deg)}.o4{left:100px;top:24px}
.ramp{position:absolute;left:24%;top:56%;width:58%;height:9px;background:linear-gradient(90deg,#8eeaff,#8f78ff);transform:rotate(-20deg);border-radius:9px;box-shadow:0 0 15px #59bffb}.ball{position:absolute;left:34%;top:29%;width:33px;height:33px;border-radius:50%;background:radial-gradient(circle at 28% 23%,#ffd7a0,#ff923d 48%,#ad3c23);box-shadow:0 0 15px #ff995566;animation:roll 3s ease-in-out infinite}.arrow{position:absolute;color:#b6f6e2;font-size:30px;font-weight:900;text-shadow:0 0 10px #5df8c5}.arrow1{left:58%;top:12%;animation:pulse 1.3s ease-in-out infinite}.arrow2{left:76%;top:42%;color:#ffcc74}
.sun{position:absolute;left:13%;top:14%;width:31px;height:31px;border-radius:50%;background:radial-gradient(circle at 25% 25%,#fff9bd,#ffb84d 65%,#e96739);box-shadow:0 0 24px #ffb84d}.cloud{position:absolute;width:73px;height:27px;border-radius:20px;background:#e3eeff;box-shadow:inset 0 -6px #a3bbed}.cloud:before,.cloud:after{content:'';position:absolute;border-radius:50%;background:#e3eeff}.cloud:before{width:30px;height:30px;top:-14px;left:10px}.cloud:after{width:38px;height:35px;top:-17px;right:7px}.cloud1{left:48%;top:24%;animation:drift 6s linear infinite}.cloud2{left:18%;top:43%;transform:scale(.65);opacity:.9}.drop{position:absolute;width:8px;height:15px;background:linear-gradient(#a9efff,#388df4);border-radius:70% 25% 70% 70%;transform:rotate(25deg);animation:rain 1.4s ease-in infinite}.d1{left:58%;top:42%}.d2{left:68%;top:48%;animation-delay:-.4s}.d3{left:75%;top:40%;animation-delay:-.8s}.ocean{position:absolute;bottom:-19px;left:0;width:100%;height:55px;border-radius:48% 48% 0 0;background:linear-gradient(#21b8e6,#1857b9)}.vapour{position:absolute;left:23%;bottom:23%;width:20px;height:34px;border-left:3px dashed #d5f8ff}
.earth{width:116px;height:116px;border-radius:50%;overflow:hidden;background:radial-gradient(circle at 30% 24%,#a7f8ff 0%,#3899f5 28%,#1750ba 65%,#08184c 100%);box-shadow:inset -18px -7px 22px #030924b8,0 0 17px #5fb5ff88;animation:spin 14s linear infinite}.land{position:absolute;background:linear-gradient(120deg,#7cf1a2,#1c9865);border-radius:48% 30% 55% 45%;box-shadow:inset -3px -4px 5px #0a553f99}.l1{width:36px;height:48px;left:25px;top:20px;transform:rotate(22deg)}.l2{width:25px;height:37px;left:58px;top:57px;transform:rotate(-17deg)}.l3{width:29px;height:21px;left:70px;top:21px;transform:rotate(15deg)}.atmosphere{position:absolute;inset:-4px;border:3px solid #88dfff;border-radius:50%;box-shadow:0 0 13px #5ec9ff}
.circuit{width:140px;height:96px}.wire{position:absolute;background:#88c9ff;box-shadow:0 0 9px #77e3ff}.wire.top{left:20px;top:5px;width:100px;height:4px}.wire.right{right:17px;top:5px;width:4px;height:84px}.wire.bottom{left:20px;bottom:7px;width:100px;height:4px}.wire.left{left:17px;top:5px;width:4px;height:84px}.battery{position:absolute;left:10px;top:34px;width:18px;height:30px;background:linear-gradient(90deg,#e5efff,#8fa6d6);border:2px solid #fff;border-radius:3px}.lamp{position:absolute;right:7px;top:31px;width:24px;height:24px;border-radius:50%;background:radial-gradient(circle,#fff8bd,#ffd44c 54%,#ac7721);box-shadow:0 0 18px #ffd44c}.electron{position:absolute;width:9px;height:9px;border-radius:50%;background:#b8fdff;box-shadow:0 0 12px #70fff6;animation:electron 2.5s linear infinite}.e1{left:40px;top:1px}.e2{left:116px;top:63px;animation-delay:-.8s}.e3{left:57px;bottom:2px;animation-delay:-1.6s}
@keyframes spin{to{transform:translate(-50%,-50%) rotateY(360deg) rotateX(22deg)}}@keyframes float{0%,100%{margin-top:0}50%{margin-top:-8px}}@keyframes electron{0%{transform:rotate(0deg) translateX(-42px)}100%{transform:rotate(360deg) translateX(-42px)}}@keyframes roll{0%,100%{left:30%;transform:rotate(0)}50%{left:63%;transform:rotate(320deg)}}@keyframes pulse{50%{transform:scale(1.2);opacity:.6}}@keyframes drift{0%{margin-left:-10px}50%{margin-left:15px}100%{margin-left:-10px}}@keyframes rain{0%{transform:translateY(-5px) rotate(25deg);opacity:0}35%{opacity:1}100%{transform:translateY(50px) rotate(25deg);opacity:0}}
</style></head><body><div class="wrap"><div class="title">${title}</div><div class="stage" id="stage"><div class="star"></div>${shape}</div><div class="caption">${description} Touch any object to reveal its name and facts. Drag to rotate.</div><div id="detail" class="detail"><strong>Explore the model</strong><br>Tap a visible object to discover what it does.</div></div>
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
'.electron':['Charge marker','The moving dots are illustrative markers, not a scale drawing of individual electrons.']
};
Object.keys(detailMap).forEach(selector=>stage.querySelectorAll(selector).forEach(el=>{
  el.style.cursor='pointer';el.setAttribute('role','button');el.setAttribute('tabindex','0');
  el.setAttribute('aria-label',detailMap[selector][0]);
  const show=()=>{detail.innerHTML='<strong>'+detailMap[selector][0]+'</strong><br>'+detailMap[selector][1];};
  el.addEventListener('click',e=>{e.stopPropagation();show()});
  el.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();show()}});
}));
let down=false,lastX=0,angle=0;
stage.addEventListener('pointerdown',e=>{down=true;lastX=e.clientX;stage.setPointerCapture(e.pointerId)});
stage.addEventListener('pointermove',e=>{if(!down)return;angle+=e.clientX-lastX;lastX=e.clientX;stage.style.perspectiveOrigin=(50+angle*.12)+'% 50%';const model=stage.querySelector('.cube,.atom,.molecule,.cell,.earth,.circuit');if(model){model.style.animationPlayState='paused';model.style.transform='translate(-50%,-50%) rotateY('+angle+'deg) rotateX('+(angle*.35)+'deg)'}});
stage.addEventListener('pointerup',()=>{down=false});
stage.addEventListener('pointercancel',()=>{down=false});
})();</script>
</body></html>
""".trimIndent()
}
