2026-03-25 13:09:07.150 13339-13339
BLASTBufferQueue_Java
com.stellar.camera
I  update, w= 720 h= 1544 mName =
VRI[MainActivity]@79fc6e mNativeObject=
0xb400006f97840000 sc.mNativeObject=
0xb400006f97923980 format= -3 caller=

android.view.ViewRootImpl.updateBlastSurface
IfNeeded:3590

android.view.ViewRootImpl.relayoutWindow:116
85

android.view.ViewRootImpl.performTraversals:
4804

android.view.ViewRootImpl.doTraversal:3924

android.view.ViewRootImpl$TraversalRunnable.
run:12903

android.view.Choreographer$CallbackRecord.ru
n:1901
2026-03-25 13:09:07.150 13339-13339 libc
com.stellar.camera                   W
Access denied finding property

"vendor.display.enable_optimal_refresh_rate"
2026-03-25 13:09:07.150 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  Relayout returned: old=(0,0,720,1544)
new=(0,0,720,1544) relayoutAsync=false
req=(720,1544)0 dur=13 res=0x3 s={true
0xb400006f97918b00} ch=true seqId=0
2026-03-25 13:09:07.151 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
D  mThreadedRenderer.initialize()
mSurface={isValid=true
0xb400006f97918b00}
hwInitialized=true
2026-03-25 13:09:07.151 13339-13339
SV[2634238...nActivity]
com.stellar.camera
I  windowStopped(false) true
android.view.SurfaceView{fb38755
V.E......
........ 0,0-720,1544 #7f080067
app:id/cameraPreview} of
VRI[MainActivity]@79fc6e
2026-03-25 13:09:07.151 13339-13339
SurfaceView
com.stellar.camera
I  263423829 Changes: creating=true
format=false size=false visible=true
alpha=false hint=false left=false
top=false
z=false attached=true
lifecycleStrategy=false
2026-03-25 13:09:07.153 13339-13339 libc
com.stellar.camera                   W
Access denied finding property

"vendor.display.enable_optimal_refresh_rate"
2026-03-25 13:09:07.153 13339-13339
BufferQueueProducer
com.stellar.camera
I  [](id:341b00000005,api:0,p:0,c:13339)
setDequeueTimeout:2077252342
2026-03-25 13:09:07.153 13339-13339
BLASTBufferQueue_Java
com.stellar.camera
I  new BLASTBufferQueue, mName= fb38755

SurfaceView[com.stellar.camera/com.stellar.c
amera.MainActivity]@0 mNativeObject=
0xb400006f8ac3a000 caller=

android.view.SurfaceView.createBlastSurfaceC
ontrols:1781

android.view.SurfaceView.updateSurface:1450

android.view.SurfaceView.setWindowStopped:53
9

android.view.SurfaceView.surfaceCreated:2327

android.view.ViewRootImpl.notifySurfaceCreat
ed:3502

android.view.ViewRootImpl.performTraversals:
5286

android.view.ViewRootImpl.doTraversal:3924

android.view.ViewRootImpl$TraversalRunnable.
run:12903

android.view.Choreographer$CallbackRecord.ru
n:1901

android.view.Choreographer$CallbackRecord.ru
n:1910
2026-03-25 13:09:07.153 13339-13339
BLASTBufferQueue_Java
com.stellar.camera
I  update, w= 720 h= 1544 mName = fb38755

SurfaceView[com.stellar.camera/com.stellar.c
amera.MainActivity]@0 mNativeObject=
0xb400006f8ac3a000 sc.mNativeObject=
0xb400006f97923740 format= 4 caller=

android.view.SurfaceView.createBlastSurfaceC
ontrols:1782

android.view.SurfaceView.updateSurface:1450

android.view.SurfaceView.setWindowStopped:53
9

android.view.SurfaceView.surfaceCreated:2327

android.view.ViewRootImpl.notifySurfaceCreat
ed:3502

android.view.ViewRootImpl.performTraversals:
5286
2026-03-25 13:09:07.153 13339-13339
SurfaceView
com.stellar.camera
I  263423829 Cur surface:
Surface(name=null
mNativeObject=0)/@0x9192283
2026-03-25 13:09:07.153 13339-13339
SurfaceComposerClient
com.stellar.camera
D  setCornerRadius ## fb38755

SurfaceView[com.stellar.camera/com.stellar.c
amera.MainActivity]@0#44338
cornerRadius=0.000000
2026-03-25 13:09:07.153 13339-13339
SV[2634238...nActivity]
com.stellar.camera
I  pST: sr = Rect(0, 0 - 720, 1544) sw =
720
sh = 1544
2026-03-25 13:09:07.153 13339-13339
SurfaceView
com.stellar.camera
D  263423829 performSurfaceTransaction
RenderWorker position = [0, 0, 720, 1544]
surfaceSize = 720x1544
2026-03-25 13:09:07.154 13339-13339 libc
com.stellar.camera                   W
Access denied finding property

"vendor.display.enable_optimal_refresh_rate"
2026-03-25 13:09:07.154 13339-13339
SV[2634238...nActivity]
com.stellar.camera
I  updateSurface: mVisible = true
mSurface.isValid() = true
2026-03-25 13:09:07.154 13339-13339
SV[2634238...nActivity]
com.stellar.camera
I  updateSurface: mSurfaceCreated = false
surfaceChanged = true visibleChanged =
true
2026-03-25 13:09:07.154 13339-13339
SurfaceView
com.stellar.camera
I  263423829 visibleChanged --
surfaceCreated
2026-03-25 13:09:07.154 13339-13339
SV[2634238...nActivity]
com.stellar.camera
I  surfaceCreated 1 #1
android.view.SurfaceView{fb38755
V.E......
........ 0,0-720,1544 #7f080067
app:id/cameraPreview}
2026-03-25 13:09:07.157 13339-13339 Toast
com.stellar.camera                   I
show: caller =

com.stellar.camera.MainActivity.tryOpenCurre
ntLens:127
2026-03-25 13:09:07.158 13339-13339 Toast
com.stellar.camera                   I
show: contextDispId = 0 mCustomDisplayId
=
-1 focusedDisplayId = 0 isActivityContext
=
true
2026-03-25 13:09:07.162 13339-13339
SurfaceView
com.stellar.camera
I  263423829 surfaceChanged -- format=4
w=720 h=1544
2026-03-25 13:09:07.162 13339-13339
SV[2634238...nActivity]
com.stellar.camera
I  surfaceChanged (720,1544) 1 #1
android.view.SurfaceView{fb38755
V.E......
........ 0,0-720,1544 #7f080067
app:id/cameraPreview}
2026-03-25 13:09:07.162 13339-13339
SurfaceView
com.stellar.camera
I  263423829 surfaceRedrawNeeded
2026-03-25 13:09:07.162 13339-13339
SurfaceView
com.stellar.camera
I  263423829 finishedDrawing
2026-03-25 13:09:07.162 13339-13339
SurfaceView
com.stellar.camera
V  Layout: x=0 y=0 w=720 h=1544,
frame=Rect(0, 0 - 720, 1544)
2026-03-25 13:09:07.163 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
D  reportNextDraw

android.view.ViewRootImpl.performTraversals:
5443

android.view.ViewRootImpl.doTraversal:3924

android.view.ViewRootImpl$TraversalRunnable.
run:12903

android.view.Choreographer$CallbackRecord.ru
n:1901

android.view.Choreographer$CallbackRecord.ru
n:1910
2026-03-25 13:09:07.163 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
D  Setup new
sync=wmsSync-VRI[MainActivity]@79fc6e#4
2026-03-25 13:09:07.163 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  Creating new active sync group
VRI[MainActivity]@79fc6e#5
2026-03-25 13:09:07.163 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
D  Start draw after previous draw not
visible
2026-03-25 13:09:07.163 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
D  registerCallbacksForSync
syncBuffer=false
2026-03-25 13:09:07.172 13339-13361
SurfaceView
com.stellar.camera
D  263423829 updateSurfacePosition
RenderWorker, frameNr = 1, position = [0,
0,
720, 1544] surfaceSize = 720x1544
2026-03-25 13:09:07.172 13339-13361
SV[2634238...nActivity]
com.stellar.camera
I  uSP: rtp = Rect(0, 0 - 720, 1544) rtsw
=
720 rtsh = 1544
2026-03-25 13:09:07.172 13339-13361
SV[2634238...nActivity]
com.stellar.camera
I  onSSPAndSRT: pl = 0 pt = 0 sx = 1.0 sy
=
1.0
2026-03-25 13:09:07.172 13339-13361
SV[2634238...nActivity]
com.stellar.camera
I  aOrMT: VRI[MainActivity]@79fc6e t =

android.view.SurfaceControl$Transaction@d938
d5e fN = 1

android.view.SurfaceView.-$$Nest$mapplyOrMer
geTransaction:0

android.view.SurfaceView$SurfaceViewPosition
UpdateListener.positionChanged:1932

android.graphics.RenderNode$CompositePositio
nUpdateListener.positionChanged:401
2026-03-25 13:09:07.172 13339-13361
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  mWNT: t=0xb400007032937b00
mBlastBufferQueue=0xb400006f97840000 fn=
1
HdrRenderState mRenderHdrSdrRatio=1.0
caller=

android.view.SurfaceView.applyOrMergeTransac
tion:1863

android.view.SurfaceView.-$$Nest$mapplyOrMer
geTransaction:0

android.view.SurfaceView$SurfaceViewPosition
UpdateListener.positionChanged:1932
2026-03-25 13:09:07.173 13339-13369
VRI[MainAc...ty]@79fc6e
com.stellar.camera
D  Received frameDrawingCallback
syncResult=0 frameNum=1.
2026-03-25 13:09:07.173 13339-13369
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  mWNT: t=0xb400006feffa4a00
mBlastBufferQueue=0xb400006f97840000 fn=
1
HdrRenderState mRenderHdrSdrRatio=1.0
caller=

android.view.ViewRootImpl$12.onFrameDraw:154
41

android.view.ThreadedRenderer$1.onFrameDraw:
718 <bottom of call stack>
2026-03-25 13:09:07.173 13339-13369
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  Setting up sync and
frameCommitCallback
2026-03-25 13:09:07.185 13339-13361
BLASTBufferQueue
com.stellar.camera
I
[VRI[MainActivity]@79fc6e#2](f:0,a:0,s:0)
onFrameAvailable the first frame is
available
2026-03-25 13:09:07.185 13339-13361
SurfaceComposerClient
com.stellar.camera
I  apply transaction with the first
frame.
layerId: 44334, bufferData(ID:
57290568761368, frameNumber: 1)
2026-03-25 13:09:07.185 13339-13361
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  Received frameCommittedCallback
lastAttemptedDrawFrameNum=1
didProduceBuffer=true
2026-03-25 13:09:07.186 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
D  reportDrawFinished seqId=0
2026-03-25 13:09:07.186 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  onDisplayChanged oldDisplayState=2
newDisplayState=2
2026-03-25 13:09:07.187 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  onDisplayChanged oldDisplayState=2
newDisplayState=2
2026-03-25 13:09:07.187 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  onDisplayChanged oldDisplayState=2
newDisplayState=2
2026-03-25 13:09:07.190 13339-13339
InsetsSourceConsumer
com.stellar.camera
I  applyRequestedVisibilityToControl:
visible=true, type=navigationBars,

host=com.stellar.camera/com.stellar.camera.M
ainActivity
2026-03-25 13:09:07.190 13339-13339
InsetsSourceConsumer
com.stellar.camera
I  applyRequestedVisibilityToControl:
visible=true, type=statusBars,

host=com.stellar.camera/com.stellar.camera.M
ainActivity
2026-03-25 13:09:07.228 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
D
mThreadedRenderer.initializeIfNeeded()#2
mSurface={isValid=true
0xb400006f97918b00}
2026-03-25 13:09:07.229 13339-13339
InputMethodManagerUtils
com.stellar.camera
D  startInputInner - Id : 0
2026-03-25 13:09:07.229 13339-13339
InputMethodManager
com.stellar.camera
I  startInputInner -

IInputMethodManagerGlobalInvoker.startInputO
rWindowGainedFocus
2026-03-25 13:09:07.234 13339-13339
InputMethodManager
com.stellar.camera
I  handleMessage: setImeVisibility
visible=false
2026-03-25 13:09:07.237 13339-13339
InsetsController
com.stellar.camera
D  hide(ime(), fromIme=false)
2026-03-25 13:09:07.237 13339-13339
ImeTracker
com.stellar.camera
I  com.stellar.camera:c17ebde4:
onCancelled
at PHASE_CLIENT_ALREADY_HIDDEN
2026-03-25 13:09:07.247 13339-13397
InputTransport
com.stellar.camera
D  Input channel constructed: 'ClientS',
fd=134
2026-03-25 13:09:07.461 13339-13397
CameraManagerGlobal
com.stellar.camera
I  Camera 2 facing CAMERA_FACING_BACK
state
now CAMERA_STATE_OPENING for client
com.stellar.camera API Level 2 User Id
0Device Id 0
2026-03-25 13:09:07.495 13339-13764
CameraManagerGlobal
com.stellar.camera
I  Camera 2 facing CAMERA_FACING_BACK
state
now CAMERA_STATE_OPEN for client
com.stellar.camera API Level 2 User Id
0Device Id 0
2026-03-25 13:09:07.512 13339-13363 libc
com.stellar.camera                   W
Access denied finding property

"vendor.display.enable_optimal_refresh_rate"
2026-03-25 13:09:07.512 13339-13363 libc
com.stellar.camera                   W
Access denied finding property

"vendor.display.enable_optimal_refresh_rate"
2026-03-25 13:09:07.727 13339-13764
CameraManagerGlobal
com.stellar.camera
I  Camera 2 facing CAMERA_FACING_BACK
state
now CAMERA_STATE_ACTIVE for client
com.stellar.camera API Level 2 User Id
0Device Id 0
2026-03-25 13:09:07.728 13339-13339 Toast
com.stellar.camera                   I
show: caller =

com.stellar.camera.MainActivity$onCreate$3.i
nvoke:79
2026-03-25 13:09:07.729 13339-13339 Toast
com.stellar.camera                   I
show: contextDispId = 0 mCustomDisplayId
=
-1 focusedDisplayId = 0 isActivityContext
=
true
2026-03-25 13:09:08.032 13339-13764
BLASTBufferQueue
com.stellar.camera
I  [fb38755

SurfaceView[com.stellar.camera/com.stellar.c
amera.MainActivity]@0#3](f:0,a:0,s:0)
onFrameAvailable the first frame is
available
2026-03-25 13:09:08.032 13339-13764
SurfaceComposerClient
com.stellar.camera
I  apply transaction with the first
frame.
layerId: 44339, bufferData(ID:
57290568761370, frameNumber: 1)
2026-03-25 13:09:08.289 13339-13339
VRI[MainAc...ty]@79fc6e
com.stellar.camera
I  handleResized,

frames=ClientWindowFrames{frame=[0,0][720,15
44] display=[0,0][720,1544]
parentFrame=[0,0][0,0]} displayId=0
dragResizing=false compatScale=1.0
frameChanged=false
attachedFrameChanged=false
configChanged=false displayChanged=false
compatScaleChanged=false
dragResizingChanged=false
---------------------------- PROCESS
ENDED
(13339) for package com.stellar.camera
----------------------------
2026-03-25 13:09:09.987  3242-3799
InputDispatcher         system_server
E  channel 'de843a1

com.stellar.camera/com.stellar.camera.MainAc
tivity' ~ Channel is unrecoverably broken
and will be disposed!
2026-03-25 13:09:09.996  3242-8067
AppOps
system_server                        E
Operation not started: uid=10026
pkg=com.stellar.camera(null) op=CAMERA
2026-03-25 13:09:10.028  3242-6984
AppOps
system_server                        E
Operation not started: uid=10026
pkg=com.stellar.camera(null) op=CAMERA
aun salen los limited esto "Los 30
segundos
solo
se desbloquean para el Sensor 0 o
sensores
con Hardware Level 3/FULL." es como
decir que yo por ser yo tengo tanta
estatura, y realmente no me doy a la
tarea
de tomar mi medida real para reportarla,
lo
mismo quiero hacer con el tiempo de
exposicion, quiero que el mismo celular
me
de el tiempo de exposicion maximo
soportado
por el harware del dispositivo, no
suponer
nada, o se consulta y me da los datos o
buscamos nuevos metodos para esto, guarda
en
tu memoria esto, la principal y la del
proyecto, no suponer, si no se tiene
acceso
a la informacion o no puedes resolver la
problematica, perdirme a mi el usuario
alguna recomentdacion, sonsultar, sugerir
investigacion o demas cosas que sean
necesarias para lograr el objetivo de una
app profesional, una app aparente mente
profesional