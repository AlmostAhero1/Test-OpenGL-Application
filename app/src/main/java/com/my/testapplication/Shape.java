package com.my.testapplication;

import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Shape {


    private FloatBuffer vertexBuffer;
    private FloatBuffer uvBuffer;

    // число координат в массиве
    static final int COORDS_PER_VERTEX = 3;
    static float coords[] = {   //координаты для вертексного шейдера
            -1f,  1f, 0.0f,
            1f, 1f, 0.0f,
            -1, -1, 0.0f,
            1f, -1f, 0.0f,
    };
    static float texPos[] = {   //текстурные координаты для фрагментного шейдера
            0f,  0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
    };


    private final int mProgram;

    private int positionHandle;

    private final int vertexCount = coords.length / COORDS_PER_VERTEX;
    private final int vertexStride = COORDS_PER_VERTEX * 4;
    GLSurfaceView parent;


    public Shape(String vertexShaderCode, String fragmentShaderCode, GLSurfaceView parent) {
        this.parent = parent;

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

        // creates OpenGL ES program executables
        GLES20.glLinkProgram(mProgram);
        //инициализация буфера вершин
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (number of coordinate values * 4 bytes per float)
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

    //передаем разрешение изображения в шейдер
    private void setResolution(Point resolution)
    {
        int resHandler = GLES20.glGetUniformLocation(mProgram,"resolution");
        GLES20.glUniform2f(resHandler,resolution.x,resolution.y);
    }

    public void draw(float param, Point resolution) {
        GLES20.glUseProgram(mProgram);
        // get handle to vertex shader's vPosition member
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        int textPosHandler = GLES20.glGetAttribLocation(mProgram, "tPos");

        // добавление шейдеров в программу
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glEnableVertexAttribArray(textPosHandler);


        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBuffer);
        GLES20.glVertexAttribPointer(textPosHandler, 2,
                GLES20.GL_FLOAT, false,
                8, uvBuffer);

        //Передаем время для анимации движения облаков(функция шума использует время)
        int iTime = GLES20.glGetUniformLocation(mProgram,"iTime");
        GLES20.glUniform1f(iTime,param);

        //передаем разрешение изображения в шейдер
        setResolution(resolution);

        //Отрисовываем
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount);

        // Выключаем массивы вершин
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textPosHandler);
    }


}