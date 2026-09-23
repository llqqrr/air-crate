/* Interactive art prototype. No game API or gameplay asset writes. */
(() => {
 'use strict';
 const $=s=>document.querySelector(s),TH=THREE;
 const canvas=$('#view'),renderer=new TH.WebGLRenderer({canvas,antialias:false,alpha:true});
 renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.outputColorSpace=TH.SRGBColorSpace;
 const scene=new TH.Scene(),camera=new TH.PerspectiveCamera(38,1,.1,500);
 scene.add(new TH.HemisphereLight(0xfff4db,0x526570,2.2));
 const light=new TH.DirectionalLight(0xffffff,2.3);light.position.set(-20,40,-30);scene.add(light);
 const assembly=new TH.Group();scene.add(assembly);
 let active='frog',pressure=8,cycleStart=-99,shots=0,mode='orbit',yaw=-.9,pitch=.38,radius=39;
 const models={},bones={},original=new Map();
 function construct(d){
  const root=new TH.Group(),groups={},byId={},materials=d.textures.map(t=>{
   const tex=new TH.TextureLoader().load(t.source);tex.magFilter=TH.NearestFilter;tex.minFilter=TH.NearestFilter;
   tex.wrapS=tex.wrapT=TH.RepeatWrapping;tex.colorSpace=TH.SRGBColorSpace;
   return new TH.MeshLambertMaterial({map:tex,side:TH.DoubleSide,alphaTest:.25});
  });
  for(const o of d.outliner){
   const group=new TH.Group();group.name=o.name;group.position.fromArray(o.origin||[0,0,0]);root.add(group);groups[o.name]=group;
   for(const id of o.children)byId[id]={group,origin:o.origin||[0,0,0]};
   original.set(group,{position:group.position.clone(),rotation:group.rotation.clone()});
  }
  for(const e of d.elements){
   const parent=byId[e.uuid],origin=parent?.origin||[0,0,0];
   for(const f of Object.values(e.faces)){
    const p=[],uv=[],ids=f.vertices,t=d.textures[f.texture];
    for(let i=1;i<ids.length-1;i++)for(const k of [ids[0],ids[i],ids[i+1]]){
     const v=e.vertices[k];p.push(v[0]-origin[0],v[1]-origin[1],v[2]-origin[2]);uv.push(f.uv[k][0]/t.width,1-f.uv[k][1]/t.height);
    }
    const geo=new TH.BufferGeometry();geo.setAttribute('position',new TH.Float32BufferAttribute(p,3));geo.setAttribute('uv',new TH.Float32BufferAttribute(uv,2));geo.computeVertexNormals();
    (parent?.group||root).add(new TH.Mesh(geo,materials[f.texture]));
   }
  }
  root.position.set(-8,-8,-7);assembly.add(root);return {root,groups};
 }
 for(const [key,data] of Object.entries(window.MODEL_DATA)){const a=construct(data);models[key]=a.root;bones[key]=a.groups;a.root.visible=key==='frog';}
 const N=window.CONFIG.names,rad=TH.MathUtils.degToRad;
 const steam=new TH.Group();models.frog.add(steam);
 for(let i=0;i<5;i++){
  const mesh=new TH.Mesh(new TH.BoxGeometry(.33,.33,.33),new TH.MeshBasicMaterial({color:0xd6ded9,transparent:true,opacity:0}));steam.add(mesh);
 }
 function sync(){
  $('#air').value=pressure;$('#count').textContent=pressure+' / 8';$('#pressure').textContent=Math.round(pressure/8*100)+'%';
  $('#warning').textContent=pressure===0?'气压不足 · 需要充气':pressure<=2?'低气压 · 指针进入红区':'压力充足';
  $('#warning').className=pressure<=2?'low':'ok';$('#fire').disabled=pressure===0||performance.now()/1000-cycleStart<1.2;
  $('#shots').textContent=shots;
 }
 function fire(){
  const now=performance.now()/1000;if(pressure===0||now-cycleStart<1.2||active!=='frog')return false;
  pressure--;shots++;cycleStart=now;sync();return true;
 }
 $('#fire').onclick=fire;$('#refill').onclick=()=>{pressure=8;sync();};$('#air').oninput=e=>{pressure=Number(e.target.value);sync();};
 function selectMode(next){mode=next;document.querySelectorAll('[data-camera]').forEach(b=>b.classList.toggle('selected',b.dataset.camera===mode));}
 for(const b of document.querySelectorAll('[data-camera]'))b.onclick=()=>selectMode(b.dataset.camera);
 $('#compare').onchange=e=>{active=e.target.value;for(const [k,v] of Object.entries(models))v.visible=k===active;$('#fire').disabled=active!=='frog';};
 $('#explode').oninput=e=>{
  const t=Number(e.target.value);const g=bones.frog;
  for(const [name,b] of Object.entries(g))b.position.copy(original.get(b).position);
  for(const name of [N.head,N.jaw,N.tongue])g[name].position.z-=t*3;
  g[N.tank].position.y+=t*3;g[N.needle].position.x-=t*1.5;
 };
 let drag=null;canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);};
 canvas.onpointerup=()=>drag=null;canvas.onpointermove=e=>{if(!drag||mode!=='orbit')return;yaw-=(e.clientX-drag[0])*.007;pitch=TH.MathUtils.clamp(pitch+(e.clientY-drag[1])*.005,-1.1,1.2);drag=[e.clientX,e.clientY];};
 canvas.onwheel=e=>{e.preventDefault();radius=TH.MathUtils.clamp(radius+e.deltaY*.025,23,65);};
 window.addEventListener('keydown',e=>{if(e.code==='Space'&&!['INPUT','SELECT'].includes(document.activeElement.tagName)){e.preventDefault();fire();}});
 function lerpKeys(t,keys){
  for(let i=1;i<keys.length;i++)if(t<=keys[i][0]){let [a,x]=keys[i-1],[b,y]=keys[i];return x+(y-x)*Math.max(0,(t-a)/(b-a));}return keys.at(-1)[1];
 }
 function animate(now){
  now/=1000;const w=canvas.clientWidth,h=canvas.clientHeight;if(canvas.width!==w*renderer.getPixelRatio()||canvas.height!==h*renderer.getPixelRatio())renderer.setSize(w,h,false);
  camera.aspect=w/h;assembly.position.set(0,0,0);assembly.rotation.set(0,0,0);assembly.scale.setScalar(1);
  if(mode==='first'){
   // Vanilla hand anchor plus the original potato-cannon display transform.
   const d=window.CONFIG.display.firstperson_righthand;
   camera.fov=70;camera.position.set(0,0,0);camera.lookAt(0,0,-20);
   assembly.position.set(.56*16+d.translation[0],-.52*16+d.translation[1],-.72*16+d.translation[2]);
   assembly.rotation.set(...d.rotation.map(rad));
  }else if(mode==='gauge'){
   camera.fov=30;camera.position.set(-12,3,1);camera.lookAt(-4,2,1);
  }else{
   camera.fov=38;camera.position.set(Math.sin(yaw)*Math.cos(pitch)*radius,Math.sin(pitch)*radius,Math.cos(yaw)*Math.cos(pitch)*-radius);camera.lookAt(0,0,0);
  }
  camera.updateProjectionMatrix();const t=now-cycleStart,g=bones.frog;
  const running=t>=0&&t<1.2;
  g[N.head].rotation.x=rad(running?lerpKeys(t,[[0,0],[.12,0],[.23,72],[.43,72],[.65,0],[1.2,0]]):0);
  g[N.head].position.z=original.get(g[N.head]).position.z-Number($('#explode').value)*3-1.8*Math.sin(g[N.head].rotation.x);
  g[N.tongue].scale.z=running?lerpKeys(t,[[0,.1],[.22,.1],[.29,4.1],[.36,4.1],[.53,.1],[1.2,.1]]):.1;
  g[N.cog].rotation.x=rad(running?lerpKeys(t,[[0,0],[.12,0],[.53,360],[.75,540],[1.2,540]]):0);
  g[N.trigger].rotation.x=rad(running?lerpKeys(t,[[0,0],[.12,-16],[.4,-16],[.65,0],[1.2,0]]):0);
  g[N.needle].rotation.x=rad(70-140*pressure/8);
  for(let i=0;i<steam.children.length;i++){
   const m=steam.children[i],p=(t-.46-i*.025)/.3;
   m.visible=running&&p>0&&p<1;m.position.set(12.1+p*2.5,8+p*1.8+i*.18,12.1+(i-2)*.19);
   m.scale.setScalar(1+p*1.6);m.material.opacity=Math.max(0,.8*(1-p));
  }
  $('#phase').textContent=!running?'待机 · 蛙嘴闭合':t<.23?'开启蛙嘴':t<.37?'舌头弹出':t<.53?'回收舌头':t<.75?'闭合 / 排气':'复位';
  $('#fire').disabled=active!=='frog'||pressure===0||running;
  renderer.render(scene,camera);requestAnimationFrame(animate);
 }
 sync();requestAnimationFrame(animate);
 window.catcherPreview={fire,refill:()=>{pressure=8;sync();},setPressure:n=>{pressure=Math.max(0,Math.min(8,n));sync();},state:()=>({pressure,shots,needleDegrees:70-140*pressure/8,low:pressure<=2,active,mode}),setMode:selectMode};
})();
