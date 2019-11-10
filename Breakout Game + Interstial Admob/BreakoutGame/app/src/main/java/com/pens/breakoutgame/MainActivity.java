package com.pens.breakoutgame;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.SoundPool;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.io.IOException;

import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private String TAG = "MainActivity";
    // gameView will be the view of the game
    // It will also hold the logic of the game
    // and respond to screen touches as well
    BreakoutView breakoutView;

    private InterstitialAd mInterstitialAd;


    private GoogleSignInClient mGoogleSignInClient = null;

    final public int BUTTON_SIGN_IN = 2;
    final public int BUTTON_SIGN_OUT = 1;


    boolean GAME_OVER = false;

    AdsCallback mCallback;

    // Request code used to invoke sign in user interactions.
    private static final int RC_SIGN_IN = 9001;
    Button signOutButton;
    SignInButton signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Initialize gameView and set it as the view
        breakoutView = new BreakoutView(this);
//        setContentView(R.layout.activity_main);
//        setContentView(breakoutView);

        // Sample AdMob app ID: ca-app-pub-3940256099942544~3347511713
        MobileAds.initialize(this, "ca-app-pub-5613566242400936~3469126473");

        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-3940256099942544/1033173712");
        mInterstitialAd.loadAd(new AdRequest.Builder().build());

        mInterstitialAd.setAdListener(new AdListener() {

            @Override
            public void onAdClosed() {
                breakoutView.createBricksAndRestart();
                mInterstitialAd.loadAd(new AdRequest.Builder().build());
            }
                                      });

        requestWindowFeature(Window.FEATURE_NO_TITLE);


        FrameLayout game = new FrameLayout(this);
        LinearLayout gameWidgets = new LinearLayout(this);

        signOutButton = new Button(this);
        signOutButton.setId(BUTTON_SIGN_OUT);
        signOutButton.setWidth(300);
        signOutButton.setText("Sign Out");
        signOutButton.setOnClickListener(this);


        mGoogleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        signInButton = new SignInButton(this);
        signInButton.setId(BUTTON_SIGN_IN);
        signInButton.setOnClickListener(this);
        signInButton.setSize(SignInButton.SIZE_STANDARD);

        AdView adView = new AdView(this);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId("ca-app-pub-3940256099942544/6300978111");


        RelativeLayout relativeLayout = new RelativeLayout(this);

        RelativeLayout.LayoutParams adViewParams = new RelativeLayout.LayoutParams(
                AdView.LayoutParams.WRAP_CONTENT,
                AdView.LayoutParams.WRAP_CONTENT);
        // align bottom
        adViewParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        // align center
        adViewParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

        relativeLayout.addView(adView, adViewParams);


        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        gameWidgets.addView(signInButton);
        gameWidgets.addView(signOutButton);

        signInButton.setVisibility(View.GONE);
        signOutButton.setVisibility(View.GONE);

        game.addView(breakoutView);
        game.addView(relativeLayout);

        game.addView(gameWidgets);

        setContentView(game);

    }

    public void displayAd()
    {
        if(mInterstitialAd.isLoaded())
        {
            mInterstitialAd.show();
        }
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        displayAd();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case BUTTON_SIGN_IN:
                startSignInIntent();
                Toast.makeText(this, "Signin", Toast.LENGTH_SHORT).show();
                break;

            case BUTTON_SIGN_OUT:
                signOut();
                break;


            default:
                break;
        }



    }
    // Here is our implementation of BreakoutView
    // It is an inner class.
    // Note how the final closing curly brace }
    // is inside the BreakoutGame class

    // Notice we implement runnable so we have
    // A thread and can override the run method.
    class BreakoutView extends SurfaceView implements Runnable {

        // This is our thread
        Thread gameThread = null;

        // This is new. We need a SurfaceHolder
        // When we use Paint and Canvas in a thread
        // We will see it in action in the draw method soon.
        SurfaceHolder ourHolder;

        // A boolean which we will set and unset
        // when the game is running- or not.
        volatile boolean playing;

        // Game is paused at the start
        boolean paused = true;

        // A Canvas and a Paint object
        Canvas canvas;
        Paint paint;

        // This variable tracks the game frame rate
        long fps;

        // This is used to help calculate the fps
        private long timeThisFrame;

        // The size of the screen in pixels
        int screenX;
        int screenY;

        // The players paddle
        Paddle paddle;

        // A ball
        Ball ball;

        // Up to 200 bricks
        Brick[] bricks = new Brick[200];
        int numBricks = 0;

        // For sound FX
        SoundPool soundPool;
        int beep1ID = -1;
        int beep2ID = -1;
        int beep3ID = -1;
        int loseLifeID = -1;
        int explodeID = -1;

        // The score
        int score = 0;

        // Lives
        int lives = 3;


        Context context;

        // When the we initialize (call new()) on gameView
        // This special constructor method runs
        public BreakoutView(Context context) {
            // The next line of code asks the
            // SurfaceView class to set up our object.
            // How kind.
            super(context);

            this.context = context;
            // Initialize ourHolder and paint objects
            ourHolder = getHolder();
            paint = new Paint();


            // Get a Display object to access screen details
            Display display = getWindowManager().getDefaultDisplay();
            // Load the resolution into a Point object
            Point size = new Point();
            display.getSize(size);


            screenX = size.x;
            screenY = size.y;
            Log.d("MainActivity", "screenX : " + screenX + " screenY : " + screenY);

            paddle = new Paddle(screenX, screenY);

            // Create a ball
            ball = new Ball(screenX, screenY);

            // Load the sounds

            // This SoundPool is deprecated but don't worry
            soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);

            try {
                // Create objects of the 2 required classes
                AssetManager assetManager = context.getAssets();
                AssetFileDescriptor descriptor;

                // Load our fx in memory ready for use
                descriptor = assetManager.openFd("beep1.ogg");
                beep1ID = soundPool.load(descriptor, 0);

                descriptor = assetManager.openFd("beep2.ogg");
                beep2ID = soundPool.load(descriptor, 0);

                descriptor = assetManager.openFd("beep3.ogg");
                beep3ID = soundPool.load(descriptor, 0);

                descriptor = assetManager.openFd("loseLife.ogg");
                loseLifeID = soundPool.load(descriptor, 0);

                descriptor = assetManager.openFd("explode.ogg");
                explodeID = soundPool.load(descriptor, 0);

            } catch (IOException e) {
                // Print an error message to the console
                Log.e("error", "failed to load sound files");
            }

            createBricksAndRestart();


        }



        public void createBricksAndRestart() {
            // Put the ball back to the start
            ball.reset(screenX, screenY);
            paddle.reset(screenX);


            int brickWidth = screenX / 8;
            int brickHeight = screenY / 10;

            // Build a wall of bricks
            numBricks = 0;
            for (int column = 0; column < 8; column++) {
                for (int row = 0; row < 3; row++) {
                    bricks[numBricks] = new Brick(row, column, brickWidth, brickHeight);
                    numBricks++;
                }
            }

            // if game over reset scores and lives
            if (lives == 0) {
                score = 0;
                lives = 3;
                GAME_OVER = false;
            }

        }

        @Override
        public void run() {
            while (playing) {

                // Capture the current time in milliseconds in startFrameTime
                long startFrameTime = System.currentTimeMillis();
                // Update the frame
                // Update the frame
                if (!paused) {
                    update();
                }

                // Draw the frame
                draw();

                // Calculate the fps this frame
                // We can then use the result to
                // time animations and more.
                timeThisFrame = System.currentTimeMillis() - startFrameTime;
                if (timeThisFrame >= 1) {
                    fps = 1000 / timeThisFrame;
                }

            }

        }

        // Everything that needs to be updated goes in here
        // Movement, collision detection etc.
        public void update() {

            // Move the paddle if required
            paddle.update(fps, screenX);

            ball.update(fps);

            // Check for ball colliding with a brick
            for (int i = 0; i < numBricks; i++) {

                if (bricks[i].getVisibility()) {


                    if (RectF.intersects(bricks[i].getRect(), ball.getRect())) {
                        bricks[i].setInvisible();
                        ball.reverseYVelocity();
                        score = score + 10;

                        soundPool.play(explodeID, 1, 1, 0, 0, 1);
                    }
                }
            }

            // Check for ball colliding with paddle
            if (RectF.intersects(paddle.getRect(), ball.getRect())) {
                ball.setRandomXVelocity();
                ball.reverseYVelocity();
                ball.clearObstacleY(paddle.getRect().top - 2);

                soundPool.play(beep1ID, 1, 1, 0, 0, 1);
            }

            // Bounce the ball back when it hits the bottom of screen
            if (ball.getRect().bottom > screenY) {
                ball.reverseYVelocity();
                ball.clearObstacleY(screenY - 2);

                // Lose a life
                lives--;
                soundPool.play(loseLifeID, 1, 1, 0, 0, 1);

                //After Game Over
                if (lives == 0) {
                    GAME_OVER = true;
                    Log.d("MainActivity", "GAMEOVER : " + GAME_OVER);
                    paused = true;
                    draw();
                    playing = false;
                    /*try {
                        gameThread.sleep(3000);
                        createBricksAndRestart();
                    } catch (InterruptedException e) {
                        Log.e("Error:", "joining thread");
                    }*/
                }
            }

            // Bounce the ball back when it hits the top of screen
            if (ball.getRect().top < 0) {
                ball.reverseYVelocity();
                ball.clearObstacleY(12);

                soundPool.play(beep2ID, 1, 1, 0, 0, 1);
            }

            // If the ball hits left wall bounce
            if (ball.getRect().left < 0) {
                ball.reverseXVelocity();
                ball.clearObstacleX(2);

                soundPool.play(beep3ID, 1, 1, 0, 0, 1);
            }

            // If the ball hits right wall bounce
            if (ball.getRect().right > screenX - 10) {
                ball.reverseXVelocity();
                ball.clearObstacleX(screenX - 22);

                soundPool.play(beep3ID, 1, 1, 0, 0, 1);
            }


            // Pause if cleared screen
            if (score == numBricks * 10) {
                paused = true;
                createBricksAndRestart();
            }

        }

        // Draw the newly updated scene
        public void draw() {

            // Make sure our drawing surface is valid or we crash
            if (ourHolder.getSurface().isValid()) {
                // Lock the canvas ready to draw
                canvas = ourHolder.lockCanvas();

                // Draw the background color
                canvas.drawColor(Color.argb(255, 26, 128, 182));

                // Choose the brush color for drawing
                paint.setColor(Color.argb(255, 255, 255, 255));

                // Draw the paddle
                canvas.drawRect(paddle.getRect(), paint);

                // Draw the ball
                canvas.drawRect(ball.getRect(), paint);


                // Change the brush color for drawing
                paint.setColor(Color.argb(255, 249, 0, 0));

                // Draw the bricks if visible
                for (int i = 0; i < numBricks; i++) {
                    if (bricks[i].getVisibility()) {
                        canvas.drawRect(bricks[i].getRect(), paint);
                    }
                }

                // Choose the brush color for drawing
                paint.setColor(Color.argb(255, 255, 255, 255));

                // Draw the score
                paint.setTextSize(40);
                canvas.drawText("Score: " + score + "   Lives: " + lives, 10, 50, paint);

                // Has the player cleared the screen?
                if (score == numBricks * 10) {
                    paint.setTextSize(90);
                    canvas.drawText("YOU HAVE WON!", 10, screenY / 2, paint);
                }

//                Log.d("MainActivity","GAMEOVER : "+GAME_OVER);

                // Has the player lost?
                if (GAME_OVER) {
                    Log.d("MainActivity", "GAMEOVER : " + GAME_OVER);
                    paint.setTextSize(90);
                    canvas.drawText("YOU HAVE LOST!", 10, screenY / 2, paint);
                }

                // Draw everything to the screen
                ourHolder.unlockCanvasAndPost(canvas);
            }

        }

        // If SimpleGameEngine Activity is paused/stopped
        // shutdown our thread.
        public void pause() {
            playing = false;
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Log.e("Error:", "joining thread");
            }

        }

        // If SimpleGameEngine Activity is started theb
        // start our thread.
        public void resume() {
            playing = true;
            gameThread = new Thread(this);
            gameThread.start();
        }

        // The SurfaceView class implements onTouchListener
        // So we can override this method and detect screen touches.
        @Override
        public boolean onTouchEvent(MotionEvent motionEvent) {

            switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {

                // Player has touched the screen
                case MotionEvent.ACTION_DOWN:

                    paused = false;

                    if (motionEvent.getX() > screenX / 2) {
                        paddle.setMovementState(paddle.RIGHT);
                    } else {
                        paddle.setMovementState(paddle.LEFT);
                    }

                    break;


                // Player has removed finger from screen
                case MotionEvent.ACTION_UP:

                    paddle.setMovementState(paddle.STOPPED);
                    break;
            }
            return true;
        }

    }
    // This is the end of our BreakoutView inner class

    // This method executes when the player starts the game
    @Override
    protected void onResume() {
        super.onResume();

        // Tell the gameView resume method to execute
        breakoutView.resume();
    }

    // This method executes when the player quits the game
    @Override
    protected void onPause() {
        super.onPause();

        // Tell the gameView pause method to execute
        breakoutView.pause();
    }


    public void startSignInIntent() {
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {

        if (requestCode == RC_SIGN_IN) {

            Task<GoogleSignInAccount> task =
                    GoogleSignIn.getSignedInAccountFromIntent(intent);

            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                onConnected(account);
            } catch (ApiException apiException) {
                String message = apiException.getMessage();
                if (message == null || message.isEmpty()) {
                    message = "There was an issue with sign in. Please try again later";
                }

                onDisconnected();

                new AlertDialog.Builder(this)
                        .setMessage(message)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();
            }
        }
    }

    private String mPlayerId;

    // The currently signed in account, used to check the account has changed outside of this activity when resuming.
    GoogleSignInAccount mSignedInAccount = null;

    private void onConnected(GoogleSignInAccount googleSignInAccount) {
        Log.d(TAG, "onConnected(): connected to Google APIs");
        Toast.makeText(this, "onConnected", Toast.LENGTH_SHORT).show();

        signInButton.setVisibility(View.GONE);
        signOutButton.setVisibility(View.VISIBLE);

        if (mSignedInAccount != googleSignInAccount) {
            mSignedInAccount = googleSignInAccount;
        }
    }

    public void onDisconnected() {
        Log.d(TAG, "onDisconnected()");

        Toast.makeText(this, "onDisconnected", Toast.LENGTH_SHORT).show();

    }

    public void signOut() {
        Log.d(TAG, "signOut()");

        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {

                        if (task.isSuccessful()) {
                            signInButton.setVisibility(View.VISIBLE);
                            signOutButton.setVisibility(View.GONE);

                            Toast.makeText(getApplicationContext(), "success", Toast.LENGTH_SHORT).show();

                            Log.d(TAG, "signOut(): success");
                        } else {

                            Toast.makeText(getApplicationContext(), "signOut failed", Toast.LENGTH_SHORT).show();

                        }

                        onDisconnected();
                    }
                });
    }

}
