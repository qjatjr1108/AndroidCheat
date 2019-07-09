package com.androidcheat.view

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.widget.FrameLayout
import com.androidcheat.BuildConfig
import com.androidcheat.R
import com.androidcheat.prefs.Prefs
import com.androidcheat.util.env.Env
import com.androidcheat.util.env.Ipv4Address
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.layout_floating_cheat_widget.view.*
import kr.nextm.lib.PreferencesHelper
import java.util.concurrent.TimeUnit


class CheatView(val service: Service, val params: WindowManager.LayoutParams) : FrameLayout(service) {
    private val windowManager: WindowManager
        get() = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private lateinit var disposable: Disposable

    init {
        View.inflate(context, R.layout.layout_floating_cheat_widget, this)

        setBuildClock()

        setSummary()

        setDetailText()

        layoutCheatExpanded.visibility = GONE

        initializeDragDrop()

        initializeButtons()
    }

    private fun setBuildClock() {
        Observable.interval(0, 1, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                textBuildTime.text = timeSinceBuild()

                val color: Int = colorByTimeElapsedSinceBuild()

                textBuildTime.setTextColor(color)
            }
    }

    private fun timeSinceBuild(): String {
        val elapsed = elapsedSinceBuild()

        val seconds = elapsed.toInt() % 60

        val minutes = elapsed.toInt() / 60

        return minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
    }

    private fun colorByTimeElapsedSinceBuild(): Int {
        val minutes = elapsedSinceBuild() / 60.0
        return when {
            minutes < 1 -> Color.WHITE
            minutes < 60 * 24 -> Color.GREEN
            minutes < 60 * 48 -> Color.YELLOW
            else -> Color.RED
        }
    }

    private fun elapsedSinceBuild(): Double {
        val diff = System.currentTimeMillis() - BuildConfig.BUILD_DATE_MILLIS
        return diff / 1000.0
    }

    private fun setDetailText() {

        textDetails.text =
            """hello world
${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}
BUILD DATE: ${BuildConfig.BUILD_DATE}
IP: ${Ipv4Address.get().hostAddress}
MODEL/SERIAL: ${Env.getModelName()}/${Env.getRawSerial()}"""

    }

    override fun onDetachedFromWindow() {
        disposable.dispose()
        super.onDetachedFromWindow()
    }

    private fun initializeDragDrop() {
        var initialX: Int = 0
        var initialY: Int = 0
        var initialTouchX: Float = 0.toFloat()
        var initialTouchY: Float = 0.toFloat()

        disposable = Observable.create<Point> { emitter ->

            var disposable: Disposable? = null
            //Drag and move floating view using user's touch action.
            root_container.setOnTouchListener(OnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {

                        disposable = Observable.timer(1, TimeUnit.SECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                // to SomeTing action longPress
                            }
                        //remember the initial position.
                        initialX = params.x
                        initialY = params.y

                        //get the touch location
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return@OnTouchListener true
                    }
                    MotionEvent.ACTION_UP -> {
                        disposable?.dispose()

                        val Xdiff = (event.rawX - initialTouchX).toInt()
                        val Ydiff = (event.rawY - initialTouchY).toInt()

                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        //So that is click event.
                        if (Xdiff < 10 && Ydiff < 10) {
                            toggleExpansionView()
                        }
                        return@OnTouchListener true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val Xdiff = (event.rawX - initialTouchX).toInt()
                        val Ydiff = (event.rawY - initialTouchY).toInt()

                        if (Xdiff > 10 || Ydiff > 10) {
                            disposable?.dispose()
                        }

                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()

                        emitter.onNext(Point(params.x, params.y))

                        //Update the layout with new X & Y coordinate
                        windowManager.updateViewLayout(this@CheatView, params)
                        return@OnTouchListener true
                    }
                }
                false
            })
        }
            .debounce(5, TimeUnit.SECONDS)
            .subscribe {
                savePosition(it)
            }
    }

    private fun toggleExpansionView() {
        refreshUi()

        if (layoutCheatExpanded.visibility == View.VISIBLE) {
            layoutCheatExpanded.visibility = View.GONE
        } else {
            setDetailText()
            layoutCheatExpanded.visibility = View.VISIBLE
        }
    }

    private fun savePosition(point: Point) {
        Prefs.save {
            it.cheat.floatingPoint = point
        }
    }

    private fun setSummary() {
        textSummary.text = """${BuildConfig.FLAVOR.capitalize()}.${BuildConfig.BUILD_TYPE.capitalize()}""".trimMargin()
        textSummary.setTextColor(Color.GREEN)
    }

    private fun initializeButtons() {

        buttonCheat.setOnClickListener {
            toggleExpansionView()
//            startActivity(CheatActivity::class.java)
        }

        buttonRestart.setOnClickListener {
            toggleExpansionView()
//            AppInstance.restart(SplashActivity::class.java)
        }

        buttonNewInstance.setOnClickListener {
//            startActivity(SplashActivity::class.java)
        }

        buttonLogView.setOnClickListener {
//            AppInstance.get().startService(Intent(AppInstance.get(), FloatingLogViewService::class.java))
        }

        buttonLaunchLastCheatActivity.setOnClickListener {
            toggleExpansionView()

            val lastCheatActivity = PreferencesHelper["LAST_CHEAT_ACTIVITY"]
            if (lastCheatActivity.isEmpty())
                return@setOnClickListener

            val componentName = ComponentName(
                service.packageName,
                lastCheatActivity
            )

            val intent = Intent(Intent.ACTION_MAIN)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.component = componentName
            service.startActivity(intent)
        }

        refreshUi()

        //Set the close button
        buttonCheatClose.setOnClickListener {
            //            Prefs.save {
//                Prefs.cheat.floatingCheatView = false
//            }
            service.stopSelf()
        }

        buttonOpen.setOnClickListener {
//            startActivity(SplashActivity::class.java)
        }

    }

    private fun refreshUi() {
        buttonLaunchLastCheatActivity.text = PreferencesHelper["LAST_CHEAT_ACTIVITY", ".Nothing"].split(".").last()
    }


    private fun startActivity(clazz: Class<*>) {
        val intent = Intent(service, clazz)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
    }

}