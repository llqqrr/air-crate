"""Software preview; camera basis corrected to Minecraft screen handedness."""
from pathlib import Path
import base64,io,json,math
import numpy as np
from PIL import Image

def read_model(path):
    d=json.loads(path.read_text(encoding='utf-8'));textures=[np.asarray(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')) for t in d['textures']];faces=[]
    for e in d['elements']:
        for f in e['faces'].values():
            points=np.array([e['vertices'][k] for k in f['vertices']],float);uv=np.array([f['uv'][k] for k in f['vertices']],float)
            faces.append((points,uv,textures[f['texture']]))
    return d,faces

def render(path,size=(520,520),camera=(1,.72,-1),ortho=False,frame_bounds=None):
    """Nearest-texel triangle rasterizer; opaque z-buffer, then translucent pass."""
    d,faces=read_model(path);W,H=size;out=np.zeros((H,W,4),dtype=float);zb=np.full((H,W),-np.inf)
    view=np.array(camera,float);view/=np.linalg.norm(view);right=np.cross([0,1,0],view)
    if np.linalg.norm(right)<.01:right=np.array([1.,0,0])
    right/=np.linalg.norm(right);up=np.cross(view,right)
    allp=np.concatenate([p for p,u,t in faces]) if frame_bounds is None else np.asarray(frame_bounds,float)
    center=(allp.min(0)+allp.max(0))/2
    basis=np.array([right,-up,view]).T;ps=(allp-center)@basis
    scale=min((W-30)/max(1,np.ptp(ps[:,0])),(H-30)/max(1,np.ptp(ps[:,1])))
    light=np.array([-.45,.8,-.65]);light/=np.linalg.norm(light)
    triangles=[]
    for p,u,t in faces:
        n=np.cross(p[1]-p[0],p[2]-p[0]);n/=max(np.linalg.norm(n),1e-9)
        # Meshes are rendered double-sided, matching transparent glass intent.
        if np.dot(n,view)<0:n=-n
        bright=1 if ortho else min(1.10,max(.60,.77+.28*np.dot(n,light)))
        q=(p-center)@basis;q[:,:2]*=scale;q[:,:2]+=np.array([W/2,H/2])
        for i in range(1,len(p)-1):triangles.append((q[[0,i,i+1]],u[[0,i,i+1]],t,bright))
    def drawtri(q,uv,t,bright,translucent):
        lo=np.maximum(np.floor(q[:,:2].min(0)).astype(int),[0,0]);hi=np.minimum(np.ceil(q[:,:2].max(0)).astype(int),[W-1,H-1])
        if np.any(hi<lo):return
        xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
        a,b,c=q;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-8:return
        w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w2=1-w0-w1
        depth=w0*a[2]+w1*b[2]+w2*c[2]
        uu=np.floor(w0*uv[0,0]+w1*uv[1,0]+w2*uv[2,0]).astype(int)%t.shape[1]
        vv=np.floor(w0*uv[0,1]+w1*uv[1,1]+w2*uv[2,1]).astype(int)%t.shape[0]
        pix=t[vv,uu].astype(float)/255;pix[:,:,:3]*=bright;pix[:,:,:3]=np.clip(pix[:,:,:3],0,1)
        sy=slice(lo[1],hi[1]+1);sx=slice(lo[0],hi[0]+1);z=zb[sy,sx];dst=out[sy,sx]
        mask=(w0>=-1e-7)&(w1>=-1e-7)&(w2>=-1e-7)&(depth>z+1e-6)
        if translucent:
            mask&=(pix[:,:,3]>0)&(pix[:,:,3]<1);alpha=pix[:,:,3:4];res=np.empty_like(dst);res[:,:,:3]=pix[:,:,:3]*alpha+dst[:,:,:3]*(1-alpha);res[:,:,3]=pix[:,:,3]+dst[:,:,3]*(1-pix[:,:,3]);dst[mask]=res[mask]
        else:
            mask&=pix[:,:,3]==1;dst[mask]=pix[mask];z[mask]=depth[mask]
    for tri in triangles:drawtri(*tri,False)
    for tri in sorted(triangles,key=lambda v:v[0][:,2].mean()):drawtri(*tri,True)
    alpha=out[:,:,3:4];out[:,:,:3]=np.divide(out[:,:,:3],alpha,out=np.zeros_like(out[:,:,:3]),where=alpha>0)
    return Image.fromarray(np.clip(out*255,0,255).astype('uint8'))

