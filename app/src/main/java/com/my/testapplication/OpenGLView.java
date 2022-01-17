package com.my.testapplication;

import android.content.Context;
import android.opengl.GLSurfaceView;

//класс для связывания вьюхи и рендерера
public class OpenGLView extends GLSurfaceView {

    private final OpenGLRenderer renderer;
    public OpenGLView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer = new OpenGLRenderer(context,getWidth(),getHeight(),this,getResources());
        setRenderer(renderer);
    }
    public OpenGLRenderer getRenderer()
    {
        return renderer;
    }

}
