/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package zhengxiao.kotlindatabindingexample.app

import android.app.Activity
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.activity_binding.*
import org.json.JSONObject
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.android.view.ViewObservable
import rx.android.widget.WidgetObservable
import rx.lang.kotlin.observable
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

/**
 * Created by gerald on 7/21/15.
 */
class BindingActivity : Activity() {

    val TAG = "binding"

    val nameObservable = BehaviorSubject.create<String>()

    val userObservable = BehaviorSubject.create<JSONObject>()

    val server = Server()

    private var loadedUser: User? by Delegates.observable(null as User?, { meta, new, old ->
        Log.d(TAG, "user is changed from ${new} -> ${old}")
    })

    /**
     * event chains:
     *
     * * search text --> nameObservable --> userObservable --> onLoadedUser() --> update listAdapter
     * * addFriend click --> server.addFriend --> Toast
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_binding)

        // reset view state
//        progressBar.hide()
        title.setText("Input Something to Search!")
        list.setLayoutManager(LinearLayoutManager(this))
        list.setAdapter(object : RecyclerView.Adapter<Holder>() {
            override fun getItemCount(): Int {
                return if (loadedUser != null) 1 else 0
            }

            override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): Holder? {
                val cardView = LayoutInflater.from(this@BindingActivity).inflate(R.layout.listitem_user, parent, false)
                return Holder(cardView)
            }

            override fun onBindViewHolder(holder: Holder?, position: Int) {
                holder?.name?.setText(loadedUser?.name)
            }

        })

        // search text --> nameObservable
        val searchStringObservable = WidgetObservable.text(search)
                .map { e -> e.text().toString() }

        searchStringObservable
                .filter { s -> s.length() >= 3 }
                .debounce(500, TimeUnit.MILLISECONDS)
                .subscribe { s -> nameObservable.onNext(s) }

        searchStringObservable.filter { s -> s.length() == 0 }.subscribe { s -> nameObservable.onNext(s) }

        // nameObservable --> userObservable
        nameObservable.subscribe { s ->
            server.findUser(s)
                    .doOnSubscribe { runOnUiThread { progressBar.show() } }
                    .doOnCompleted { runOnUiThread { progressBar.hide() } }
                    .subscribe { jo: JSONObject ->
                        userObservable.onNext(jo)
                    }
        }

        userObservable.map { jo ->
            if (server.isOK(jo)) {
                User(jo.getString("name"), jo.getInt("age"))
            } else {
                null
            }
        }.observeOn(AndroidSchedulers.mainThread()).subscribe { user ->
            didLoadUser(user)
        }

        ViewObservable.clicks(addFriend).subscribe { e ->
            if (loadedUser != null) {
                server.addFriend(loadedUser!!)
                        .doOnSubscribe { runOnUiThread { progressBar.show() } }
                        .doOnCompleted { runOnUiThread { progressBar.hide() } }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { jo: JSONObject ->
                            if (server.isOK(jo)) {
                                Toast.makeText(this@BindingActivity, "added ${jo.getString("name")}", Toast.LENGTH_LONG).show()
                                search.setText("")
                            } else {
                                Toast.makeText(this@BindingActivity, "added nothing", Toast.LENGTH_LONG).show()
                            }
                        }
            }
        }
    }

    fun didLoadUser(user: User?) {
        loadedUser = user
        list.getAdapter().notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView?
        val age: TextView?

        init {
            name = view.findViewById(R.id.person_name) as TextView?
            age = view.findViewById(R.id.person_age) as TextView?
        }
    }


    class User(val name: String, val age: Int)

    class Server {

        fun findUser(s: String): Observable<JSONObject> {

            return observable<JSONObject>{ sub ->
                if (Thread.currentThread() === Looper.getMainLooper().getThread())
                    throw IllegalStateException("network on Main")

                val jo = JSONObject()
                jo.put("name", s)
                jo.put("age", (Math.random() * 10).toInt())
                SystemClock.sleep(2000L)
                sub.onNext(jo)
                sub.onCompleted()
            }.subscribeOn(Schedulers.io())
        }

        fun isOK(jo: JSONObject?): Boolean {
            return jo != null
        }

        fun addFriend(user: User): Observable<JSONObject> {
            return observable<JSONObject>{ sub ->
                if (Thread.currentThread() === Looper.getMainLooper().getThread())
                    throw IllegalStateException("network on Main")
                val jo = JSONObject()
                jo.put("name", "JsonO${user.name}")
                SystemClock.sleep(2000L)
                sub.onNext(jo)
                sub.onCompleted()
            }.subscribeOn(Schedulers.io())
        }
    }

}
