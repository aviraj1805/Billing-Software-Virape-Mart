package com.virpemart.billing.ui.common;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.virpemart.billing.AppContext;

import javafx.util.Callback;

/**
 * Creates screen controllers for FXML files. A controller that has a constructor taking
 * {@link AppContext} receives the shared app context; otherwise its no-argument constructor is used.
 */
public final class ControllerFactory implements Callback<Class<?>, Object> {

    private final AppContext context;

    public ControllerFactory(AppContext context) {
        this.context = context;
    }

    @Override
    public Object call(Class<?> type) {
        try {
            try {
                Constructor<?> withContext = type.getConstructor(AppContext.class);
                return withContext.newInstance(context);
            } catch (NoSuchMethodException e) {
                return type.getConstructor().newInstance();
            }
        } catch (ReflectiveOperationException e) {
            Throwable cause = (e instanceof InvocationTargetException ite) ? ite.getCause() : e;
            throw new IllegalStateException("Could not create controller " + type.getName(), cause);
        }
    }
}
