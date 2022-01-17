package com.my.testapplication;

import static android.opengl.GLES20.GL_CULL_FACE;
import static android.opengl.GLES20.glDisable;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class OpenGLRenderer implements GLSurfaceView.Renderer {

    Context context;
    int width = 0;
    int height = 0;
    GLSurfaceView parent;
    Bitmap bmp;
    Point resolution = new Point();

    //Буферы и их текструры. На одной отрисовывается облака, но не выводим на экран.
    //на втором блендерим текстуры земли(сферы с текстурой) и облаков
    int[] screenTex = new int[2];
    int[] fbo = new int[2];

    public OpenGLRenderer(Context context, int w, int h, GLSurfaceView parentView,Resources r)
    {
        width = w;
        height = h;
        this.context = context;
        parent = parentView;
        //Берем изображение земли из ресурсов получаем разрешение
        bmp = BitmapFactory.decodeResource(r,R.drawable.earth);
        resolution.set(bmp.getWidth(),bmp.getHeight());
    }
    //Сфера
    Sphere mSphere;
    //Облака
    Shape shape;
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.7f,1f,0.7f,1f);
        //Создаем объекты передавая необходимые коды шейдеров.
        //vertexShaderCode - простейший код приравнивания координат с учетом матрицы проекции
        //vertexShaderCode_noProj - тот же код только без матрицы
        //cloudsFragmentShaderCode - алгоритм генерации облаков с анимацией движения с прередачей времени в шейдер
        //fragmentShaderCode - пиксельный шейдер организует натягивание текстуры на сферу а потом блендеринг текстур земли и облаков
        shape = new Shape(vertexShaderCode_noProj,cloudsFragmentShaderCode,parent);
        mSphere = new Sphere(vertexShaderCode,fragmentShaderCode,bmp,parent);
    }

    //При изменении размера меняем разрешение
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        this.width = width;
        this.height = height;
        if(width>0&&height>0)
        {
            GLES20.glGenFramebuffers(2,fbo,0);
            GLES20.glGenTextures(2,screenTex,0);
            createFrameBuffer(fbo[0],screenTex[0]);
        }
        Matrix.frustumM(projectionMatrix, 0, -1, 1, -1.5f, 1.5f, 1f, 20f);
    }

    //отрисовка в процессе работы приложения
    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        //собственно в ней и рисуем
        draw();
    }

    //матрица проекции
    private final float[] vPMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];

    float rotate = 0;
    //получаем время для рисования облаков
    long startRender = System.currentTimeMillis();
    private void draw() {
        float t = System.currentTimeMillis() % 100000 / 1000f;
        //меняем буфер
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        //отключаю чтобы избежать непонятных артефактов с просвечиванием текстуры
        glDisable(GL_CULL_FACE);
        //рисуем облака
        shape.draw(t, resolution);
        //меняем буфер
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        Matrix.setLookAtM(viewMatrix, 0, 0, 0f, 2f, 0f, 0f, 0f, 0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        if(System.currentTimeMillis()-startRender>50) {
            startRender = System.currentTimeMillis();
            rotate++;
            rotate%=360;
        }
        //поворачиваем облака
        Matrix.rotateM(vPMatrix, 0, rotate, 0f, 1f, 0f);

        //включаю обратно чтобы избежать еще большего количества артефактов с налаживанием текстур
        GLES20.glEnable(GL_CULL_FACE);
        //конечная рисовка на сфере
        mSphere.draw(vPMatrix,screenTex[0],(float)rotate/360);
    }

    //вертексный шейдер без матрицы
    private final String vertexShaderCode_noProj =
            "attribute vec4 vPosition;" +
                    "attribute vec2 tPos;" +
                    "varying vec2 aPos;"+
                    "void main() {" +
                    "  gl_Position = vPosition;" +
                    "  aPos = tPos;" +
                    "}";

    //простейший код приравнивания координат с учетом матрицы проекции
    private final String vertexShaderCode =
            "attribute vec4 vPosition;" +
                    "attribute vec2 tPos;" +
                    "uniform mat4 uMVPMatrix;"+
                    "varying vec2 aPos;"+
                    "void main() {" +
                    "  gl_Position = vPosition * uMVPMatrix;" +
                    "  aPos = tPos;" +
                    "}";

    //создаем текстуру земли и блендерим с текстурой облаков
    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "varying vec2 aPos;" +
                    "uniform sampler2D image;"+
                    "uniform sampler2D upTexture;"+
                    "uniform float rotate;"+
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "vec4 topColor = texture2D(upTexture,vec2(mod(aPos.x+rotate,1.0),aPos.y));" +
                    "vec4 baseColor = texture2D(image,aPos);" +
                    "  gl_FragColor = vec4(1.)-(vec4(1.)-baseColor)*(vec4(1.)-topColor);" +
                    "}";

    //алгоритм облаков
    private final String cloudsFragmentShaderCode =
            "precision mediump float;" +
                    "varying vec2 aPos;"+
                    "uniform sampler2D image;"+
                    "uniform vec4 vColor;" +
                    "float cloudscale = 0.5;" +
                    "float speed = 0.03;" +
                    "float clouddark = 0.5;" +
                    "float cloudlight = 0.3;" +
                    "float cloudcover = 0.2;" +
                    "float cloudalpha = 0.0;" +
                    "float skytint = 0.5;" +
                    "uniform float iTime;" +
                    "vec2 iResolution = vec2(1280., 1920.);" +
                    "vec3 skycolour1 = vec3(0.0, 0.0, 0.0);" +
                    "vec3 skycolour2 = vec3(0.0, 0.0, 0.0);" +
                    "mat2 m = mat2( 1.6,  1.2, -1.2,  1.6 );" +
                    "vec2 hash( vec2 p ) {" +
                    "p = vec2(dot(p,vec2(127.1,311.7)), dot(p,vec2(269.5,183.3)));" +
                    "return -1.0 + 2.0*fract(sin(p)*43758.5453123);" +
                    "}" +
                    "float noise( in vec2 p ) {" +
                    "const float K1 = 0.366025404;" + // (sqrt(3)-1)/2;
                    "const float K2 = 0.211324865;" + // (3-sqrt(3))/6;
                    "vec2 i = floor(p + (p.x+p.y)*K1);" +
                    "vec2 a = p - i + (i.x+i.y)*K2;" +
                    "vec2 o = (a.x>a.y) ? vec2(1.0,0.0) : vec2(0.0,1.0);" +
                    "vec2 b = a - o + K2;" +
                    "vec2 c = a - 1.0 + 2.0*K2;" +
                    "vec3 h = max(0.5-vec3(dot(a,a), dot(b,b), dot(c,c) ), 0.0 );" +
                    "vec3 n = h*h*h*h*vec3( dot(a,hash(i+0.0)), dot(b,hash(i+o)), dot(c,hash(i+1.0)));" +
                    "return dot(n, vec3(70.0));" +
                    "}" +
                    "float fbm(vec2 n) {" +
                    "float total = 0.0, amplitude = 0.1;" +
                    "for (int i = 0; i < 7; i++) {" +
                    "total += noise(n) * amplitude;" +
                    "n = m * n;" +
                    "amplitude *= 0.4;" +
                    "}" +
                    "return total;" +
                    "}" +
                    "void main(void)" +
                    "{" +
                    "vec2 p = gl_FragCoord.xy / iResolution.xy;" +
                    "vec2 uv = p*vec2(iResolution.x/iResolution.y,1.0);" +
                    "float time = iTime * speed;" +
                    "float q = fbm(uv * cloudscale * 0.5);" +
                    //ridged noise shape
                    "float r = 0.0;" +
                    "uv *= cloudscale;" +
                    "uv -= q - time;" +

                    "float weight = 0.8;" +

                    "for (int i=0; i<8; i++){" +
                    "r += abs(weight*noise( uv ));" +
                    "uv = m*uv + time;" +
                    "weight *= 0.7;" +
                    "}" +
                    //noise shape
                    "float f = 0.0;" +
                    "uv = p*vec2(iResolution.x/iResolution.y,1.0);" +
                    "uv *= cloudscale;" +
                    "uv -= q - time;" +
                    "weight = 0.7;" +
                    "for (int i=0; i<8; i++){" +
                    "f += weight*noise( uv );" +
                    "uv = m*uv + time;" +
                    "weight *= 0.6;" +
                    "}" +
                    " f *= (r + f);" +
                    //noise colour
                    "float c = 0.0;" +
                    "time = iTime * speed * 2.0;" +
                    "uv = p*vec2(iResolution.x/iResolution.y,1.0);" +
                    "uv *= cloudscale*2.0;" +
                    "uv -= q - time;" +
                    "weight = 0.4;" +
                    "for (int i=0; i<7; i++){" +
                    "c += weight*noise( uv );" +
                    "uv = m*uv + time;" +
                    "weight *= 0.6;" +
                    "}" +
                    //noise ridge colour
                    "float c1 = 0.0;" +
                    "time = iTime * speed * 3.0;" +
                    "uv = p*vec2(iResolution.x/iResolution.y,1.0);" +
                    "uv *= cloudscale*3.0;" +
                    "uv -= q - time;" +
                    "weight = 0.4;" +
                    "for (int i=0; i<7; i++){" +
                    "c1 += abs(weight*noise( uv ));" +
                    "uv = m*uv + time;" +
                    "weight *= 0.6;" +
                    "}" +
                    "c += c1;" +
                    "vec3 skycolour = mix(skycolour2, skycolour1, p.y);" +
                    "vec3 cloudcolour = vec3(1.1, 1.1, 0.9) * clamp((clouddark + cloudlight*c), 0.0, 1.0);" +
                    "f = cloudcover + (cloudalpha*f*r);" +
                    "vec3 result = mix(skycolour, clamp(skytint * skycolour + cloudcolour, 0.0, 1.0), clamp(f + c, 0.0, 1.0));" +
                    "gl_FragColor = vec4( result, 1.0 );"+
                    "}";

    //Для доп задания. Здесь простой высокочастотный пространственный фильтр выделения контуров
    private final String contrastFragmentShaderCode =
            "precision mediump float;" +
                    "varying vec2 aPos;"+
                    "uniform sampler2D image;"+
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "vec4 color = vec4(0.);" +
                    "mat3 kernel = mat3(" +
                    "-1.,-1.,-1.," +
                    "-1.,9.,-1.," +
                    "-1.,-1.,-1." +
                    ");" +
                    "for(float y = -1.;y<=1.;y+=1.)" +
                    "   for(float x = -1.;x<=1.;x+=1.)" +
                    "   color += texture2D(image,aPos+vec2(x,y)/resolution)*kernel[1+int(y)][1+int(x)];" +
                    "gl_FragColor = color;" +
                    "}";

    //функция создания буфера и привязки к ним текстур
    private void createFrameBuffer(int fbo, int screenTex){
        //биндим передаваемый буфер и текстуру
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,screenTex);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        //создания пустой картинки
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
        );
        //создание текстуры
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                screenTex,
                0
        );
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
    }
}
