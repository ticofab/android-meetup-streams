package io.ticofab.meetupstream;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;

import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Streaming;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MeetupApi";

    // the interface to the stream endpoint
    interface MeetupAPI {
        @GET("rsvps")
        @Streaming
        Observable<ResponseBody> meetupStream();
    }

    // the service to access the api
    MeetupAPI mMeetupAPI = new Retrofit.Builder()
            .baseUrl("http://stream.meetup.com/2/")
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeetupAPI.class);

    // flag to signal the end of our subscription
    // this is a hack I used to get things done quick - but it isn't the way things should be done!
    Boolean mSubscribed = false;

    // Gson for conversion to object
    Gson mGson = new GsonBuilder().create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    void listen() {
        // only subscribe to the stream if we're not already listening
        if (!mSubscribed) {
            mSubscribed = true;
            mMeetupAPI.meetupStream()
                    .subscribeOn(Schedulers.newThread())
                    .flatMap(responseBody -> events(responseBody.source()))
                    .map(item -> mGson.fromJson(item, RSVP.class))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(item -> Log.d(TAG, item.toString()),
                            error -> Log.e(TAG, error.getMessage()),
                            () -> Log.d(TAG, "onComplete"));
        }
    }

    public Observable<String> events(BufferedSource source) {
        // an observable to read events one by one
        return Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                try {
                    while (!source.exhausted()) {
                        if (mSubscribed) {
                            subscriber.onNext(source.readUtf8Line());
                        } else {
                            // no longer subscribed: break out of the loop
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    subscriber.onError(e);
                }
                subscriber.onCompleted();
            }
        });
    }

    @OnClick(R.id.listen)
    void listenClick() {
        listen();
    }

    @OnClick(R.id.enough)
    void enoughClick() {
        mSubscribed = false;
    }

    public class RSVP {
        @SerializedName("response")
        String mResponse;

        @SerializedName("member")
        Member mMember;

        @Override
        public String toString() {
            return mMember.mName + " says " + mResponse + "!";
        }
    }

    public class Member {
        @SerializedName("member_name")
        String mName;
    }
}
