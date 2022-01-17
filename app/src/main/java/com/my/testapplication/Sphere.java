package com.my.testapplication;

import static androidx.core.math.MathUtils.clamp;
import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import android.graphics.Bitmap;

import android.graphics.PointF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

import androidx.core.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

public class Sphere {


    private FloatBuffer vertexBuffer;
    private FloatBuffer uvBuffer;

    // число коорлинат в массиве
    static final int COORDS_PER_VERTEX = 3;

    //координаты для вертексного шейдера
    static float[] coords;
    //текстурные координаты для фрагментного шейдера
    static float[] texPos;


    //Запоминание координат точек для вертексного шейдера
    private static void addPoint(ArrayList<Float> coords,float x,float y,float z){
        coords.add(x);
        coords.add(y);
        coords.add(z);
    }

    //Для запоминания текстурных координат для фрагментного шейдера
    private static void addPoint(ArrayList<Float> coords,float x,float y){
        coords.add(x);
        coords.add(y);
    }

    //Так как opengl координаты от -1 до 1 нормализуем значение координаты точки
    private static float normalizedToRange(float x, float start,float end){
        return start+x*(end-start);
    }

    //Функция получения сферы реализуемая через последовательное рисование кругов с полюсов до середины
    static public Pair<float[],float[]> createSphere(int size){
        ArrayList<Float> coords = new ArrayList();
        ArrayList<Float> texPos = new ArrayList();
        PointF[] circle = new PointF[size];
        PointF[] circleTexPos = new PointF[size+1];
        for (int j = 0; j < size; j++) {
            circle[j] = new PointF(
                    (float) cos(2 * PI * (float) j / size),
                    (float)sin(2 * PI * (float) j / size)
            );
            circleTexPos[j]= new PointF(
                    1f-clamp(((float)j / size),0f,1f),
                    1f-clamp(((float)j / size),0f,1f)
                    );
        }
        circleTexPos[circleTexPos.length-1]= new PointF(
                0f,
                0f
        );

        //цикл получения точек кругов
        float center = size/2f;
        for(int i = 0; i < size;i++) {
            for (int j = 0; j < size; j++) {

                float radiusTop =
                        (float)sqrt(1f-pow((i-center)/center,2));
                float radiusLow =
                        (float)sqrt(1f-pow((i+1-center)/center,2));

                int jPlus1 = (j+1)%size;


                addPoint(coords,
                        circle[j].x * radiusTop,
                        normalizedToRange((float) i / size, -1f, 1f),
                        circle[j].y * radiusTop);
                addPoint(coords,
                        circle[jPlus1].x * radiusTop,
                        normalizedToRange((float) i / size, -1f, 1f),
                        circle[jPlus1].y * radiusTop);
                addPoint(coords,
                        circle[j].x * radiusLow,
                        normalizedToRange((float) (i + 1) / size, -1f, 1f),
                        circle[j].y * radiusLow);

                addPoint(coords,
                        circle[jPlus1].x * radiusTop,
                        normalizedToRange((float) i / size, -1f, 1f),
                        circle[jPlus1].y * radiusTop);
                addPoint(coords,
                        circle[jPlus1].x * radiusLow,
                        normalizedToRange((float) (i + 1) / size, -1f, 1f),
                        circle[jPlus1].y * radiusLow);
                addPoint(coords,
                        circle[j].x * radiusLow,
                        normalizedToRange((float) (i + 1) / size, -1f, 1f),
                        circle[j].y * radiusLow);


                addPoint(texPos, circleTexPos[j].x, circleTexPos[i].y);
                addPoint(texPos, circleTexPos[j+1].x, circleTexPos[i].y);
                addPoint(texPos, circleTexPos[j].x, circleTexPos[i+1].y);

                addPoint(texPos, circleTexPos[j+1].x, circleTexPos[i].y);
                addPoint(texPos, circleTexPos[j+1].x, circleTexPos[i+1].y);
                addPoint(texPos, circleTexPos[j].x, circleTexPos[i+1].y);
            }
        }

        //Перевод vec<Float> в float[]
        float[] coordArr = new float[coords.size()];
        float[] texPosArr = new float[texPos.size()];

        for(int i = 0; i < coords.size();i++)
            coordArr[i] = coords.get(i);
        for(int i = 0; i < texPos.size();i++)
            texPosArr[i] = texPos.get(i);

        return new Pair<>(coordArr,texPosArr);
    }


    private final int mProgram;

    private int positionHandle;
    private int texture;

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
    GLSurfaceView parent;


    public Sphere(String vertexShaderCode, String fragmentShaderCode, Bitmap bmp, GLSurfaceView parent) {
        Pair<float[],float[]> pair = createSphere(256);
        coords = pair.first;
        texPos = pair.second;
        this.parent = parent;

        //Создаем текстуру из передаваемого изображения
        texture = genearteTexture(bmp);

        //Загружаем наши переданные вертексный и шейдерный коды в шейдеры
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,
                vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode);

        // создание пустой OpenGL ES программы
        mProgram = GLES20.glCreateProgram();
        String error = GLUtils.getEGLErrorString(GLES20.glGetError());
        System.out.println("shader creating error: "+error);
        // добавление шейдеров в программу
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);

        GLES20.glLinkProgram(mProgram);
        ByteBuffer bb = ByteBuffer.allocateDirect(
                coords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        //инициализация фрагментного буфера
        ByteBuffer bbtex = ByteBuffer.allocateDirect(texPos.length*4);
        bbtex.order(ByteOrder.nativeOrder());

        // Из буферов байтов делаем буферы флота
        vertexBuffer = bb.asFloatBuffer();
        uvBuffer = bbtex.asFloatBuffer();

        uvBuffer.put(texPos);
        uvBuffer.position(0);
        vertexBuffer.put(coords);
        vertexBuffer.position(0);
        
    }

    //подгружаем коды шейдеров в шейдеры
    public static int loadShader(int type, String shaderCode){
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }

    //из bmp генерируем текстуру
    public int genearteTexture(Bitmap bm)
    {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture,0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bm,0);
        return texture[0];
    }


    //передаем текстуру в параметры шейдера.
    private void setTexture(String texName,int textureId,int texture)
    {
        int texHandler = GLES20.glGetUniformLocation(mProgram,texName);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0+textureId);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glUniform1i(texHandler,textureId);
    }

    //Функция отрисовки сферы с натянутой текстурой земли и происходит блендеринг с передаваемой
    //текстурой облаков
    public void draw(float[] mvp ,int overTexture,float rotate) {
        GLES20.glUseProgram(mProgram);
        // передаем параметры
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        int textPosHandler = GLES20.glGetAttribLocation(mProgram, "tPos");

        int matHandler = GLES20.glGetUniformLocation(mProgram,"uMVPMatrix");
        GLES20.glUniformMatrix4fv(matHandler,1,false,mvp,0);
        int rotHandler = GLES20.glGetUniformLocation(mProgram,"rotate");
        GLES20.glUniform1f(rotHandler,rotate);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        // добавление шейдеров в программу
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glEnableVertexAttribArray(textPosHandler);

        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBuffer);
        GLES20.glVertexAttribPointer(textPosHandler, 2,
                GLES20.GL_FLOAT, false,
                8, uvBuffer);

        setTexture("image",0,texture);
        setTexture("upTexture",1,overTexture);


        // Собственно фенкция рисования
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, coords.length / COORDS_PER_VERTEX);

        // Выключаем массивы вершин
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textPosHandler);
    }

}
