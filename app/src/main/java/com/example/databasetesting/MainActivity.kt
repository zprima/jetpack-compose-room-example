package com.example.databasetesting

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.preferredHeight
import androidx.compose.foundation.lazy.LazyColumnFor
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.viewModel
import androidx.lifecycle.*
import androidx.room.*
import com.example.databasetesting.ui.DatabaseTestingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "first_name") val firstName: String?,
    @ColumnInfo(name = "last_name") val lastName: String?
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAll(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id IN (:userIds)")
    suspend fun loadAllByIds(userIds: IntArray): List<User>

    @Query("SELECT * FROM users WHERE first_name LIKE :first AND " +
        "last_name LIKE :last LIMIT 1")
    suspend fun findByName(first: String, last: String): User

    @Insert
    suspend fun insertAll(vararg users: User)

    @Delete
    suspend fun delete(user: User)
}

@Database(entities = arrayOf(User::class), version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    
    companion object{
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase{
            val tempInstance = INSTANCE
            if(tempInstance != null) {
                return tempInstance
            }
            
            synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "database-name"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}

class UserRepository(private val userDao: UserDao){
    val allUsers: Flow<List<User>> = userDao.getAll()
    
    suspend fun addUser(firstName: String, lastName: String){
        userDao.insertAll(User(firstName = firstName, lastName = lastName, id = 0))
    }
    
    suspend fun removeUser(user: User){
        userDao.delete(user)
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: AppViewModel by viewModels()
        vm.addUser(firstName = "G", lastName = "G")
        
        setContent {
            DatabaseTestingTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    App()
                }
            }
        }
    }
}

class AppViewModel(application: Application): AndroidViewModel(application){
    private val allUsers: Flow<List<User>>
    private val repository: UserRepository
    
    init {
        val userDao = AppDatabase.getDatabase(application).userDao()
        repository = UserRepository(userDao = userDao)
        allUsers = repository.allUsers
    }
    
    val users = allUsers
    
    fun addUser(firstName: String, lastName: String){
        viewModelScope.launch(Dispatchers.IO) { 
            repository.addUser(firstName = firstName, lastName = lastName)
        }
    }
    
    fun removeUser(user: User){
        viewModelScope.launch(Dispatchers.IO){
            repository.removeUser(user)
        }
    }
}


@Composable
fun App(){
    val appViewModel: AppViewModel = viewModel()
    val users = appViewModel.users.collectAsState(initial = listOf())

    Column() {
        UserList(users.value)
        Spacer(modifier = Modifier.preferredHeight(10.dp))
        Button(onClick = {
            appViewModel.addUser(firstName = "M", lastName = "B")
        }) {
            Text("Add User")
        }

        Spacer(modifier = Modifier.preferredHeight(10.dp))
        Button(onClick = {
            appViewModel.removeUser(users.value.last())
        }) {
            Text("Remove User")
        }
    }
}

@Composable
fun UserList(users: List<User>){
    LazyColumnFor(items = users) {
        Text("${it.firstName} ${it.lastName} - ${it.id}")
    }
}




