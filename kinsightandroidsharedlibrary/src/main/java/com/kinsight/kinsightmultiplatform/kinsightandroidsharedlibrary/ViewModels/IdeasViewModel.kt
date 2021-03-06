package com.kinsight.kinsightmultiplatform.kinsightandroidsharedlibrary.ViewModels;


import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kinsight.kinsightmultiplatform.kinsightandroidsharedlibrary.ViewModels.IdeasViewModel.NotificationParams.RELOAD
import com.kinsight.kinsightmultiplatform.kinsightandroidsharedlibrary.ViewModels.Notifications.NotificationHelper
import com.kinsight.kinsightmultiplatform.kinsightandroidsharedlibrary.ViewModels.Notifications.OnNotificationListener
import com.kinsight.kinsightmultiplatform.models.IdeaModel
import com.kinsight.kinsightmultiplatform.models.NotificationMessage
import com.kinsight.kinsightmultiplatform.repository.IdeaRepository
import kotlinx.coroutines.*


class IdeasViewModel (val app: Application, private val userName: String, private val notificationListener: OnNotificationListener) : AndroidViewModel(app) {

    companion object NotificationParams {
        const val RELOAD = "RELOAD"
        const val NEW_IDEA = "NEWIDEA"
        const val PRICE_OBJECTIVE_ACHIEVED = "PRICEOBJECTIVE"
    }

    private val serverApiUrl = "http://35.239.179.43:8081"// BuildConfig.url

    private var isSubscribedToLiveUpdates: Boolean = false

    private val ideaRep by lazy { IdeaRepository(serverApiUrl) }

    private val ideas: MutableLiveData<List<IdeaModel>> by lazy {
        MutableLiveData<List<IdeaModel>>().also {
            loadIdeas(userName, !isSubscribedToLiveUpdates)
        }
    }

    private fun loadIdeas() {
        Log.i("APP", "loading ideas")
        CoroutineScope(Dispatchers.IO).launch {
            delay(500)
            ideas.postValue(ideaRep.fetchIdeas().filter { it.isActive }.sortedByDescending { it.alpha })
        }
    }

    private fun loadIdeas(createdBy: String?, subsribeToLiveUpdates: Boolean) {
        viewModelScope.launch() {
            doLoadIdeas()
            if (subsribeToLiveUpdates) {
                subscribeToLiveUpdates()
            }
        }
    }

    private suspend fun doLoadIdeas() {
        var ideasTemp: List<IdeaModel>? = null

        withContext(Dispatchers.IO) {
            ideasTemp = ideaRep.fetchIdeas()
        }
        delay(500)
        ideas.value = ideasTemp?.filter { it.isActive }?.sortedByDescending { it.alpha }

        /*
         * this is temp, just to test ticker search and graph reading
         * will be moved into appropriate places
         * wrapped in try catch so it doesn't crash if you're pointing to a diff server that
         * doesn't support those apis (yet)
         */
        withContext(Dispatchers.IO) {
            val tickers = ideaRep.fetchTickers("AB")
            println("tickers: $tickers")
            try {
                //  val graph = ideaRep.fetchGraph(11)
                //  println(graph)
                //  println(graph.benchmark[0].tickDate())
            } catch (e: Throwable) {
                println(e.message)
            }
        }
    }

    private fun notifyOnPriceChanged() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                /*    NotificationHelper.sendNotification(

          app,
           "Alpha Capture",
                   "Price Changed",
                   "Price Changed",
                   false
                   )

*/
            }
        }
    }

    private fun notifyOnNewIdeaCreated(serverMessage: String) {
        val notificationMessage = getUserNotificationMessage(serverMessage)
        if (notificationMessage.by.toLowerCase() == "dmitri") {
            println("ping back on own new idea")
            return
        }
        notificationListener.onNotification(notificationMessage)
        /* viewModelScope.launch {
                 withContext(Dispatchers.Default) {
                         /*   NotificationHelper.sendNotification(
                                 getApplication(),
                                 "Alpha Capture", notificationMessage.message, notificationMessage.message, false, notificationMessage.ideaId

                         )
                              */
                 }
         }*/
    }

    private fun getUserNotificationMessage(serverMessage: String): NotificationMessage {
        val notificationMessageArray = serverMessage.split("|")
        val notificationMessage = NotificationMessage(
            notificationMessageArray[0],
            notificationMessageArray[1],
            notificationMessageArray[2],
            notificationMessageArray[3],
            notificationMessageArray[4].toInt()
        )
        return notificationMessage
    }


    private suspend fun subscribeToLiveUpdates() {
        withContext(Dispatchers.IO) {
            ideaRep.receive("35.239.179.43", 8081) {
                println("android app received from server: $it")

                val upperCasedMessage = it.toUpperCase()

                if (upperCasedMessage == RELOAD) {
                    loadIdeas()
                    /* do not pop up notification, only flash price color changes*/
                    // notifyOnPriceChanged()
                } else if (upperCasedMessage.startsWith(NEW_IDEA)) {
                    loadIdeas()
                    notifyOnNewIdeaCreated(it)
                } else if (upperCasedMessage.startsWith(PRICE_OBJECTIVE_ACHIEVED)) {
                    loadIdeas()
                    notifyOnNewPriceObjectiveAchieved(it)
                }
                isSubscribedToLiveUpdates = true
            }
        }
    }

    private  fun notifyOnNewPriceObjectiveAchieved(serverMessage: String){
        val notificationMessage = getUserNotificationMessage(serverMessage)
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                /*     NotificationHelper.sendNotification(
                             getApplication(),
                             "Alpha Capture", notificationMessage.message, notificationMessage.message, false
                     )
             */
            }
        }
    }

    //region public area
    fun getIdeas(): LiveData<List<IdeaModel>> = ideas

    fun getIdea(id: Int) = ideas.value!!.filter { it.id == id }.single()

    fun nextId() = ideas.value!!.maxBy { it.id }!!.id
//endregion
}

/*
data class NotificationMessage(val messageHeader: String, val message: String,
                               val by: String, val from: String, val ideaId: Int)*/











