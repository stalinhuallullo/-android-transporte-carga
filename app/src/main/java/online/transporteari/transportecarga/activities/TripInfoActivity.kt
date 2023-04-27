package online.transporteari.transportecarga.activities

import android.content.Intent
import android.content.res.Resources
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.example.easywaylocation.EasyWayLocation
import com.example.easywaylocation.Listener
import com.example.easywaylocation.draw_path.DirectionUtil
import com.example.easywaylocation.draw_path.PolyLineDataBean
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import online.transporteari.transportecarga.R
import online.transporteari.transportecarga.databinding.ActivityTripInfoBinding
import online.transporteari.transportecarga.models.Prices
import online.transporteari.transportecarga.providers.ConfigProvider

class TripInfoActivity : AppCompatActivity(), OnMapReadyCallback, Listener, DirectionUtil.DirectionCallBack {

    private lateinit var binding: ActivityTripInfoBinding
    private var googleMap: GoogleMap? = null
    private var easyWayLocation: EasyWayLocation? = null

    private var extraOriginName: String = ""
    private var extraDestinationName: String = ""
    private var extraOriginLat: Double = 0.0
    private var extraOriginLng: Double = 0.0
    private var extraDestinationLat: Double = 0.0
    private var extraDestinationLng: Double = 0.0

    private var originLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null

    private var wayPoints: ArrayList<LatLng> = ArrayList()
    private val WAY_POINT_TAG: String = "way_point_tag"
    private lateinit var direcctionUtil: DirectionUtil

    private var markerOrigin: Marker? = null
    private var markerDestination: Marker? = null
    private var configProvider: ConfigProvider = ConfigProvider()

    private var distance = 0.0
    private var time = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // EXTRAS
        extraOriginName = intent.getStringExtra("origin")!!
        extraDestinationName = intent.getStringExtra("destination")!!
        extraOriginLat = intent.getDoubleExtra("origin_lat", 0.0)
        extraOriginLng = intent.getDoubleExtra("origin_lng", 0.0)
        extraDestinationLat = intent.getDoubleExtra("destination_lat", 0.0)
        extraDestinationLng = intent.getDoubleExtra("destination_lng", 0.0)

        originLatLng = LatLng(extraOriginLat, extraOriginLng)
        destinationLatLng = LatLng(extraDestinationLat, extraDestinationLng)


        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val locationRequest = LocationRequest.create().apply {
            interval = 0
            fastestInterval = 0
            priority = Priority.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 1f
        }
        easyWayLocation = EasyWayLocation(this,locationRequest, false, false, this)

        binding.textViewOrigin.text = extraOriginName
        binding.textViewDestination.text = extraDestinationName

        // CLICKING
        binding.imageViewBack.setOnClickListener{
            finish()
        }
        binding.btnConfirmRequest.setOnClickListener{
            goToSearchDriver()
        }

    }

    private fun goToSearchDriver() {
        if(originLatLng != null && destinationLatLng != null) {
            val intent = Intent(this, SearchActivity::class.java)
            intent.putExtra("origin", extraOriginName)
            intent.putExtra("destination", extraDestinationName)
            intent.putExtra("origin_lat", originLatLng?.latitude)
            intent.putExtra("origin_lng", originLatLng?.longitude)
            intent.putExtra("destination_lat", destinationLatLng?.latitude)
            intent.putExtra("destination_lng", destinationLatLng?.longitude)
            intent.putExtra("time", time)
            intent.putExtra("distance", distance)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Debes seleccionar el origen y el destino", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPrices(distance: Double, time: Double) {
        configProvider.gerPrices().addOnSuccessListener {document ->
            if(document.exists()) {
                val prices = document.toObject(Prices::class.java)

                val totalDistance = distance * prices?.km!!
                val totalTime = time * prices?.min!!
                var total = totalDistance + totalTime
                total = if(total < 5.0) prices?.minValue!! else total

                var minTotal = total - prices?.difference!!
                var maxTotal = total + prices?.difference!!

                val minTotalString = String.format("%.2f", minTotal)
                val maxTotalString = String.format("%.2f", maxTotal)

                binding.textViewPrice.text = "S/ $minTotalString - $maxTotalString"
            }
        }

    }

    private fun addOriginMarker() {
        markerOrigin = googleMap?.addMarker(
            MarkerOptions()
                .position(originLatLng!!)
                .title("Mi posisicón")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.icons_location_person))
        )

    }
    private fun addDestinationMarker() {
        markerDestination = googleMap?.addMarker(
            MarkerOptions()
               .position(destinationLatLng!!)
               .title("Llegada")
               .icon(BitmapDescriptorFactory.fromResource(R.drawable.icons_pin))
        )
    }

    private fun easyDrawRoute() {
        wayPoints.add(originLatLng!!)
        wayPoints.add(destinationLatLng!!)
        direcctionUtil = DirectionUtil.Builder()
            .setDirectionKey(resources.getString(R.string.google_maps_key))
            .setOrigin(originLatLng!!)
            .setWayPoints(wayPoints)
            .setGoogleMap(googleMap!!)
            .setPolyLinePrimaryColor(R.color.black)
            .setPolyLineWidth(10)
            .setPathAnimation(true)
            .setCallback(this)
            .setDestination(destinationLatLng!!)
            .build()

        direcctionUtil.initPath()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true

        googleMap?.moveCamera(
            CameraUpdateFactory.newCameraPosition(
            CameraPosition.builder().target(originLatLng!!).zoom(13f).build()
        ))
        easyDrawRoute()
        addOriginMarker()
        addDestinationMarker()

        try {
            val success = googleMap?.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this,R.raw.style_map_silver)
            )
            if(!success!!){
                Log.d("MAPAS", "No se encontro los estilos del mapa")
            }
        } catch (e: Resources.NotFoundException) {
            Log.d("MAPAS", "Error ${e.toString()}")
        }

    }

    override fun locationOn() {
    }

    override fun currentLocation(location: Location?) {

    }

    override fun locationCancelled() {
    }

    override fun onDestroy() {
        super.onDestroy()
        easyWayLocation?.endUpdates()
    }

    override fun pathFindFinish(
        polyLineDetailsMap: HashMap<String, PolyLineDataBean>,
        polyLineDetailsArray: ArrayList<PolyLineDataBean>
    ) {
        distance = polyLineDetailsArray[1].distance.toDouble() // metros
        time = polyLineDetailsArray[1].time.toDouble() // segundos

        distance = if(distance < 1000.0) 1000.0 else distance
        time = if(time < 60.0) 60.0 else time

        distance = distance / 1000.0 // KM
        time = time / 60 // MINUTES
        val timeString = String.format("%.2f", time)

        getPrices(distance, time)
        binding.textViewTimeAndDistance.text = "$timeString mins - $distance km"

        direcctionUtil.drawPath(WAY_POINT_TAG)

    }
}