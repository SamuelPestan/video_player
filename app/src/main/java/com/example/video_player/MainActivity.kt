package com.example.video_player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {

    private lateinit var videoView: VideoView
    private lateinit var bSelectVideo: ImageView
    private lateinit var playPauseButton: ImageView
    private lateinit var forwardButton: ImageView
    private lateinit var rewindButton: ImageView
    private lateinit var nextButton: ImageView
    private lateinit var prevButton: ImageView
    private lateinit var controlsContainer: LinearLayout
    private lateinit var seekBar: SeekBar

    private val listVideos = mutableListOf<String>()
    private var currentVideoIndex = -1
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var isBottomSheetOpen = false

    private val handler: Handler = Handler(Looper.getMainLooper())  // Handler para manejar el retraso

    // Directorio donde se guardarán los videos
    private val videosDirectory: File by lazy {
        File(filesDir, "video")  // Usamos el directorio de archivos internos
    }

    // Declara un launcher para abrir el selector de archivos
    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val fileName = getFileName(it)

            val destinationFile = getUniqueFileName(videosDirectory, fileName)

            try {
                inputStream?.use { input -> destinationFile.outputStream().use { output -> input.copyTo(output) } }
                updateList()
            } catch (e: IOException) {
                Toast.makeText(this, "Error al guardar el video", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        videoView = findViewById(R.id.videoView)
        bSelectVideo = findViewById(R.id.selectVideo)

        playPauseButton = findViewById(R.id.play)
        forwardButton = findViewById(R.id.forward)
        rewindButton = findViewById(R.id.backward)
        nextButton = findViewById(R.id.next)
        prevButton = findViewById(R.id.previous)
        controlsContainer = findViewById(R.id.controlsContainer)
        seekBar = findViewById(R.id.videoSeekBar)

        // Crea el directorio en caso de que no exista
        if (!videosDirectory.exists()) {
            videosDirectory.mkdirs()
        }

        updateList()
        hideControls()
    }

    override fun onStart() {
        super.onStart()

        bSelectVideo.setOnClickListener {
            if (!isBottomSheetOpen) {
                showBottomSheetDialog()
            }
        }

        playPauseButton.setOnClickListener {
            if (currentVideoIndex == -1) {
                Toast.makeText(this, "Selecciona un video primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (videoView.isPlaying) {
                videoView.pause()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            } else {
                videoView.start()
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        forwardButton.setOnClickListener {
            val newPosition = videoView.currentPosition + 5000  // Avanza 5 segundos
            videoView.seekTo(newPosition)
            seekBar.progress = newPosition
        }

        rewindButton.setOnClickListener {
            val newPosition = (videoView.currentPosition - 5000).coerceAtLeast(0) // Retrocede 5 segundos
            videoView.seekTo(newPosition)
            seekBar.progress = newPosition        }

        nextButton.setOnClickListener { playNextVideo() }
        prevButton.setOnClickListener { playPreviousVideo() }

        // Mostrar los controles cuando el usuario toque el video
        videoView.setOnClickListener {
            showControls()
            resetHideControlsTimer()  // Reiniciar el temporizador para ocultar los controles
        }

        // Actualizar el SeekBar mientras el video se reproduce
        videoView.setOnPreparedListener {
            // Establecer el valor máximo del SeekBar como la duración total del video
            seekBar.max = videoView.duration

            // Actualizar el SeekBar mientras el video se reproduce
            handler.postDelayed(updateSeekBarRunnable, 1000)
        }

        videoView.setOnCompletionListener {
            // Detener el Runnable cuando el video haya terminado
            handler.removeCallbacks(updateSeekBarRunnable)
        }

        // Configurar el listener del SeekBar para cambiar la posición del video
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoView.seekTo(progress)  // Cambiar la posición del video
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                
            }
        })
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    override fun onResume() {
        super.onResume()
        // Reiniciar el Runnable cuando la actividad se reanuda
        if (videoView.isPlaying) {
            handler.postDelayed(updateSeekBarRunnable, 1000)
        }
    }

    // Guarda la duración de la canción cuando se le da la vuelta
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentPosition = videoView.currentPosition
        outState.putInt("video_position", currentPosition)
    }

    // Recupera la instancia guardada
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val position = savedInstanceState.getInt("video_position")
        videoView.seekTo(position)
        videoView.start() // Opcional: Inicia el video automáticamente
    }

    private fun updateList() {
        // Limpiar la lista de videos antes de agregar nuevas
        listVideos.clear()
        videosDirectory.listFiles()?.filter { it.isFile && it.extension == "mp4" }?.forEach { listVideos.add(it.name) }
    }

    private fun playSelectedFile(uri: Uri) {
        try {
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            videoView.setVideoURI(uri)
            videoView.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al reproducir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("InflateParams")
    private fun showBottomSheetDialog() {
        bottomSheetDialog = BottomSheetDialog(this).apply {
            val view = layoutInflater.inflate(R.layout.bottom_sheet_layout, null)
            setContentView(view)
            isBottomSheetOpen = true  // Evita que se abra más de una vez

            val listVideoView: ListView = view.findViewById(R.id.listVideoView)
            val adapter = ArrayAdapter(this@MainActivity, R.layout.list_item, listVideos)
            listVideoView.adapter = adapter

            listVideoView.setOnItemClickListener { _, _, position, _ ->
                currentVideoIndex = position
                val selectedVideo = listVideos[position]
                val videoUri = Uri.fromFile(File(videosDirectory, selectedVideo))
                playSelectedFile(videoUri)
                dismiss()
            }

            view.findViewById<Button>(R.id.btnAdd).setOnClickListener { openFileLauncher.launch("video/*") }

            setOnDismissListener { isBottomSheetOpen = false }
            show()
        }
    }

    // Reproduce si hay el video anterior
    private fun playNextVideo() {
        if (listVideos.isNotEmpty()) {
            currentVideoIndex = (currentVideoIndex + 1) % listVideos.size
            playSelectedFile(Uri.fromFile(File(videosDirectory, listVideos[currentVideoIndex])))
        }
    }

    // Reproduce si hay el video anterior
    private fun playPreviousVideo() {
        if (listVideos.isNotEmpty()) {
            currentVideoIndex = if (currentVideoIndex > 0) currentVideoIndex - 1 else listVideos.size - 1
            playSelectedFile(Uri.fromFile(File(videosDirectory, listVideos[currentVideoIndex])))
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex != -1) it.getString(nameIndex) else "video.mp4"
        } ?: "video.mp4"
    }

    private fun getUniqueFileName(directory: File, fileName: String): File {
        var newFile = File(directory, fileName)
        var index = 1
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "")

        while (newFile.exists()) {
            val newName = if (extension.isNotEmpty()) "$nameWithoutExt($index).$extension" else "$nameWithoutExt($index)"
            newFile = File(directory, newName)
            index++
        }
        return newFile
    }

    private fun hideControls() {
        // Ocultar los botones
        playPauseButton.visibility = ImageView.INVISIBLE
        forwardButton.visibility = ImageView.INVISIBLE
        rewindButton.visibility = ImageView.INVISIBLE
        controlsContainer.visibility = LinearLayout.INVISIBLE
    }

    // Muestra los controles con una animación cuando el usuario clica sobre el video
    private fun showControls() {
        val fadeIn = ObjectAnimator.ofFloat(controlsContainer, "alpha", 0f, 1f)
        fadeIn.duration = 500  // Duración de la animación (en milisegundos)
        fadeIn.start()

        // Mostrar los botones
        playPauseButton.visibility = ImageView.VISIBLE
        forwardButton.visibility = ImageView.VISIBLE
        rewindButton.visibility = ImageView.VISIBLE
        controlsContainer.visibility = LinearLayout.VISIBLE
    }


    private fun resetHideControlsTimer() {
        handler.removeCallbacks(hideControlsRunnable)  // Eliminar cualquier retraso anterior
        handler.postDelayed(hideControlsRunnable, 3000)  // Ocultar controles después de 3 segundos
    }

    // Runnable para ocultar los controles
    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (videoView.isPlaying) {
                seekBar.progress = videoView.currentPosition  // Actualizar la posición del SeekBar
                handler.postDelayed(this, 1000)  // Actualizar cada segundo (sin recursividad directa)
            }
        }
    }
}
