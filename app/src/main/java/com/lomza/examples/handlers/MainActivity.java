package com.lomza.examples.handlers;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Main activity with methods to demonstrates the usage of Handlers, Runnables, and Messages :)
 *
 * @author Antonina Tkachuk
 */
public class MainActivity extends AppCompatActivity {
    private TextView tv01;
    private TextView tv02;
    private TextView tv03;
    private TextView tv04;
    private TextView tv05;
    private TextView tv06;
    private Button button06;

    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private Handler currentThreadHandler;
    private Handler decorViewHandler;
    private final ChangeTextHandler customHandler = new ChangeTextHandler(this);
    private final Handler notLeakyHandler = new Handler();
    private final Runnable notLeakyRunnable = new ChangeTextRunnable(this, "Hi from leak-safe Runnable!");

    private static final String TAG = "[Handlers]";
    private static final String BUNDLE_KEY = "greeting";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
    }

    @Override
    protected void onStart() {
        super.onStart();

        postTaskWithOrdinaryThread();
        postTaskWithHandlerOnMainThread();
        postTaskWithHandlerOnCurrentThread();
        //postTaskWithHandlerOnBackgroundThread();
        postTaskWithNotLeakyHandlerAndRunnable();
        sendMessageToChangeTextHandler();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // when posting Runnables or Messages, always remember to call removeCallbacks() or removeMessages()
        // or removeCallbacksAndMessages() for both.
        // this ensures that all pending tasks don't execute in vain; for instance, the user has left our activity
        // and he doesn't really care if some job is finished or not, so it's our responsibility to cancel it

        // pass null to remove ALL callbacks and messages
        mainThreadHandler.removeCallbacks(null);
        currentThreadHandler.removeCallbacks(null);
        if (decorViewHandler != null)
            decorViewHandler.removeCallbacks(null);
        customHandler.removeCallbacksAndMessages(null);
        notLeakyHandler.removeCallbacks(notLeakyRunnable);
    }

    private void initView() {
        tv01 = (TextView) findViewById(R.id.tv_01);
        tv02 = (TextView) findViewById(R.id.tv_02);
        tv03 = (TextView) findViewById(R.id.tv_03);
        tv04 = (TextView) findViewById(R.id.tv_04);
        tv05 = (TextView) findViewById(R.id.tv_05);
        tv06 = (TextView) findViewById(R.id.tv_06);
        button06 = (Button) findViewById(R.id.button_06);
        button06.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                postTaskWithThisWindowAndTextViewHandlers();
            }
        });
    }

    private void postTaskWithOrdinaryThread() {
        Runnable notForHandlerTask = new Runnable() {
            @Override
            public void run() {
                // as written here - https://developer.android.com/guide/components/processes-and-threads.html#Threads,
                // do NOT access the Android UI toolkit from outside the UI thread, as sth unexpected may happen
                // for instance, you might get android.view.ViewRootImpl$CalledFromWrongThreadException
                tv01.setText("Hi from Thread(runnable)!");
                // if you call thread.run(), this would be TRUE, as no new Thread would be created
                // read the explanation here - http://stackoverflow.com/a/35264580/655275
                Log.d(TAG, "[postTaskWithOrdinaryThread] Current looper is a main thread (UI) looper: "
                        + (Looper.myLooper() == Looper.getMainLooper()));
            }
        };
        Thread thread = new Thread(notForHandlerTask);
        thread.start();
    }

    @UiThread
    private void postTaskWithHandlerOnMainThread() {
        Runnable mainThreadTask = new Runnable() {
            @Override
            public void run() {
                // since we use Looper.getMainLooper(), we can safely update the UI from here
                tv02.setText("Hi from Handler(Looper.getMainLooper()) post!");
                Log.d(TAG, "[postTaskWithHandlerOnMainThread] Current looper is a main thread (UI) looper: "
                        + (Looper.myLooper() == Looper.getMainLooper()));
            }
        };
        mainThreadHandler.post(mainThreadTask);
    }

    private void postTaskWithHandlerOnCurrentThread() {
        currentThreadHandler = new Handler();
        Runnable currentThreadTask = new Runnable() {
            @Override
            public void run() {
                // since we use current thread (and from onCreate(), it's the UI thread), we can safely update the UI from here
                tv03.setText("Hi from Handler() post!");
                Log.d(TAG, "[postTaskWithHandlerOnCurrentThread] Current looper is a main thread (UI) looper: "
                        + (Looper.myLooper() == Looper.getMainLooper()));
            }
        };
        currentThreadHandler.post(currentThreadTask);

    }

    @UiThread
    private void postTaskWithThisWindowAndTextViewHandlers() {
        // this line will return null from onCreate() (and even if called from onResume()) and cause NPE when trying to post();
        // this is because the handler isn't attached to the view if it's not fully visible
        decorViewHandler = getWindow().getDecorView().getHandler();
        Runnable decorViewTask = new Runnable() {
            @Override
            public void run() {
                // View's post() uses UI handler internally
                tv06.post(new Runnable() {
                    @Override
                    public void run() {
                        tv06.setText("Hi from getWindow().getDecorView().getHandler() > TextView.post()!");
                        Log.d(TAG, "[postTaskWithThisWindowAndTextViewHandlers] Current looper is a main thread (UI) looper: "
                                + (Looper.myLooper() == Looper.getMainLooper()));
                    }
                });
            }
        };
        decorViewHandler.post(decorViewTask);
    }

    private void postTaskWithHandlerOnBackgroundThread() {
        final Runnable pretendsToBeSomeOtherTask = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "[postTaskWithHandlerOnBackgroundThread] Is there a looper? " + (Looper.myLooper() != null));
                // you'll get java.lang.RuntimeException: Can't create handler inside thread that has not called Looper.prepare()
                // this is because Handlers need to have a Looper associated with them; when the task was run on the main thread,
                // main thread looper was used, but if we call this from the background thread, there is NO looper to use
                // read more - https://developer.android.com/reference/android/os/Looper.html

                postTaskWithHandlerOnCurrentThread();
            }
        };
        final Thread thread = new Thread(pretendsToBeSomeOtherTask);
        thread.start();
    }

    private void postTaskWithNotLeakyHandlerAndRunnable() {
        // in order to eliminate leaks, both Handler and Runnable should be static
        // static inner classes do not hold an implicit reference to the outer class
        // it seems like a lot of useless work, but it's the most accurate and bug-free way
        // read more - http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
        notLeakyHandler.postDelayed(notLeakyRunnable, 500);
    }

    private static class ChangeTextRunnable implements Runnable {
        private final WeakReference<MainActivity> activity;
        private final String greetingMessage;

        public ChangeTextRunnable(MainActivity activity, String greetingMessage) {
            this.activity = new WeakReference<>(activity);
            this.greetingMessage = greetingMessage;
        }

        public void run() {
            if (greetingMessage == null) {
                Log.e(TAG, "The message is null ChangeTextRunnable.run()!");
                return;
            }

            MainActivity activity = this.activity.get();
            if (activity == null) {
                Log.e(TAG, "Activity is null ChangeTextRunnable.run()!");
                return;
            }

            activity.tv05.setText(greetingMessage);
        }
    }

    // === OBTAIN AND HANDLE A MESSAGE ===
    private void sendMessageToChangeTextHandler() {
        Message messageToSend = customHandler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_KEY, "Hi from custom inner Handler!");
        messageToSend.setData(bundle);
        messageToSend.what = 4;
        customHandler.sendMessage(messageToSend);
    }

    private static class ChangeTextHandler extends Handler {
        private final WeakReference<MainActivity> activity;

        public ChangeTextHandler(MainActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = this.activity.get();
            if (activity == null) {
                Log.e(TAG, "Activity is null ChangeTextHandler.handleMessage()!");
                return;
            }

            final String text = (String) msg.getData().get(BUNDLE_KEY);
            if (!TextUtils.isEmpty(text)) {
                switch (msg.what) {
                    case 4:
                        activity.tv04.setText(text);
                        break;
                    default:
                        activity.tv01.setText(text);
                        break;
                }
            }
        }
    }
    // === END - OBTAIN AND HANDLE A MESSAGE ===
}
