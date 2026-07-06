package com.questiontaker.data

import android.content.Context
import com.questiontaker.data.model.Option
import com.questiontaker.data.model.Question
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var questions: List<Question> = emptyList()

    private val sharedPrefs = context.getSharedPreferences("question_taker_prefs", Context.MODE_PRIVATE)

    init {
        // Load local asset questions synchronously first
        questions = loadQuestionsFromAssets()

        // Fetch latest questions from GitHub raw URL asynchronously in the background
        Thread {
            try {
                val githubUrl = "https://raw.githubusercontent.com/JICA98/questiontaker/main/core/data/src/main/assets/questions.json"
                val connection = java.net.URL(githubUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                    val newList = parseQuestionsJson(jsonString)
                    if (newList.isNotEmpty()) {
                        questions = newList
                        android.util.Log.d("QuestionRepository", "Successfully synced ${newList.size} questions from GitHub")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("QuestionRepository", "Failed to sync questions from GitHub, using local fallback", e)
            }
        }.start()
    }

    private fun loadQuestionsFromAssets(): List<Question> {
        return try {
            val jsonString = context.assets.open("questions.json").bufferedReader().use { it.readText() }
            parseQuestionsJson(jsonString)
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseQuestionsJson(jsonString: String): List<Question> {
        val list = mutableListOf<Question>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.getInt("id")
                val questionText = jsonObject.getString("question")
                val answer = jsonObject.getString("answer")
                val explanation = jsonObject.getString("explanation")
                
                val optionsArray = jsonObject.getJSONArray("options")
                val options = mutableListOf<Option>()
                for (j in 0 until optionsArray.length()) {
                    val optObj = optionsArray.getJSONObject(j)
                    options.add(
                        Option(
                            key = optObj.getString("key"),
                            text = optObj.getString("text")
                        )
                    )
                }

                val sourcesArray = jsonObject.getJSONArray("sources")
                val sources = mutableListOf<String>()
                for (k in 0 until sourcesArray.length()) {
                    sources.add(sourcesArray.getString(k))
                }

                list.add(
                    Question(
                        id = id,
                        question = questionText,
                        options = options,
                        answer = answer,
                        explanation = explanation,
                        sources = sources
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getAllQuestions(): List<Question> = questions

    fun getQuestionsBySource(source: String): List<Question> {
        return questions.filter { it.sources.contains(source) }
    }

    fun getAllSources(): List<String> {
        return questions.flatMap { it.sources }.distinct().sorted()
    }

    // Bookmarks Management
    fun isBookmarked(questionId: Int): Boolean {
        val bookmarkedSet = sharedPrefs.getStringSet("bookmarked_ids", emptySet()) ?: emptySet()
        return bookmarkedSet.contains(questionId.toString())
    }

    fun toggleBookmark(questionId: Int): Boolean {
        val bookmarkedSet = sharedPrefs.getStringSet("bookmarked_ids", emptySet()) ?: emptySet()
        val mutableSet = bookmarkedSet.toMutableSet()
        val isStarred: Boolean
        if (mutableSet.contains(questionId.toString())) {
            mutableSet.remove(questionId.toString())
            isStarred = false
        } else {
            mutableSet.add(questionId.toString())
            isStarred = true
        }
        sharedPrefs.edit().putStringSet("bookmarked_ids", mutableSet).apply()
        return isStarred
    }

    fun getBookmarkedQuestions(): List<Question> {
        val bookmarkedSet = sharedPrefs.getStringSet("bookmarked_ids", emptySet()) ?: emptySet()
        val ids = bookmarkedSet.mapNotNull { it.toIntOrNull() }.toSet()
        return questions.filter { ids.contains(it.id) }
    }

    // Progress/Stats Management
    fun getCompletedQuestionsCount(): Int {
        return sharedPrefs.getStringSet("completed_ids", emptySet())?.size ?: 0
    }

    fun getAccuracy(): Int {
        val totalAttempts = sharedPrefs.getInt("total_attempts", 0)
        val correctAttempts = sharedPrefs.getInt("correct_attempts", 0)
        if (totalAttempts == 0) return 0
        return (correctAttempts * 100) / totalAttempts
    }

    fun recordAttempt(questionId: Int, isCorrect: Boolean) {
        val completedSet = sharedPrefs.getStringSet("completed_ids", emptySet()) ?: emptySet()
        val mutableCompleted = completedSet.toMutableSet()
        mutableCompleted.add(questionId.toString())
        
        val totalAttempts = sharedPrefs.getInt("total_attempts", 0) + 1
        val correctAttempts = sharedPrefs.getInt("correct_attempts", 0) + if (isCorrect) 1 else 0

        sharedPrefs.edit()
            .putStringSet("completed_ids", mutableCompleted)
            .putInt("total_attempts", totalAttempts)
            .putInt("correct_attempts", correctAttempts)
            .apply()
    }

    fun resetStats() {
        sharedPrefs.edit()
            .remove("completed_ids")
            .remove("total_attempts")
            .remove("correct_attempts")
            .remove("bookmarked_ids")
            .apply()
    }
}
