package online.transporteari.transportecarga.activities


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Point
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.easywaylocation.EasyWayLocation
import com.example.easywaylocation.Listener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.SphericalUtil
import online.transporteari.transportecarga.R
import online.transporteari.transportecarga.databinding.ActivityMapBinding
import online.transporteari.transportecarga.models.Booking
import online.transporteari.transportecarga.models.DriverLocation
import online.transporteari.transportecarga.providers.AuthProvider
import online.transporteari.transportecarga.providers.BookingProvider
import online.transporteari.transportecarga.providers.GeoProvider
import online.transporteari.transportecarga.utils.CarMoveAnim
import org.imperiumlabs.geofirestore.callbacks.GeoQueryEventListener


class MapActivity : AppCompatActivity(), OnMapReadyCallback, Listener {

    private lateinit var binding: ActivityMapBinding
    private var googleMap: GoogleMap? = null
    private var easyWayLocation: EasyWayLocation? = null
    private var myLocationLatLng: LatLng? = null
    private var geoProvider = GeoProvider()
    private val authProvider = AuthProvider()
    private val bookingProvider = BookingProvider()

    // GOOGLE MAP PLACE
    private var places:PlacesClient? = null
    private var autocompleteOrigin: AutocompleteSupportFragment? = null
    private var autocompleteDestination: AutocompleteSupportFragment? = null
    private var originName: String = ""
    private var destinationName: String = ""
    private var originLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null

    private var isLocationEnabled: Boolean = false
    private var driverMarkers: ArrayList<Marker> = ArrayList()
    private var driverLocations: ArrayList<DriverLocation> = ArrayList()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val locationRequest = LocationRequest.create().apply {
            interval = 0
            fastestInterval = 0
            priority = Priority.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 1f
        }
        easyWayLocation = EasyWayLocation(this,locationRequest, false, false, this)

        localtionPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        startGooglePlaces()
        removeBooking()

        binding.btnRequestTrip.setOnClickListener{
            goToTripInfo()
        }
    }


    val localtionPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permission ->
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            when{
                permission.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ->{
                    Log.d("LOCALIZACION", "Permiso concedido")
                    easyWayLocation?.startLocation()
                }
                permission.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) ->{
                    Log.d("LOCALIZACION", "Permiso concedido con limitacion")
                    easyWayLocation?.startLocation()
                }
                else ->{
                    Log.d("LOCALIZACION", "Permiso no concedido")
                }
            }
        }

    }

    private fun removeBooking() {
        bookingProvider.getBooking().get().addOnSuccessListener { document ->
            if(document.exists()) {
                val booking = document.toObject(Booking::class.java)
                if(booking?.status == "create" || booking?.status == "cancel") {
                    bookingProvider.remove()
                }
            }
        }
    }

    /*
    * Buscando conductores por un radio de localizacion
    * */
    private fun getNearbyDrivers() {
        if(myLocationLatLng  == null) return

        geoProvider.getNearbyDrivers(myLocationLatLng!!, 100.0).addGeoQueryEventListener(object: GeoQueryEventListener {
            override fun onGeoQueryError(exception: Exception) {

            }

            override fun onGeoQueryReady() {

            }

            override fun onKeyEntered(documentID: String, location: GeoPoint) {
                for (marker in driverMarkers){
                    if(marker.tag != null){
                        if(marker.tag == documentID){
                            return
                        }
                    }
                }

                // Creando un nuevo marcador para un conductor conectado
                val driverLatLng = LatLng(location.latitude, location.longitude)
                val marker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(driverLatLng)
                        .title("Conductor disponible")
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.uber_car))
                )

                marker?.tag = documentID
                driverMarkers.add(marker!!)

                val driverLoc = DriverLocation()
                driverLoc.id = documentID
                driverLocations.add(driverLoc)

            }

            override fun onKeyExited(documentID: String) {
                for (marker in driverMarkers){
                    if(marker.tag != null){
                        if(marker.tag == documentID){
                            marker.remove()
                            driverMarkers.remove(marker)
                            driverLocations.removeAt(getPositionDriver(documentID))
                            return
                        }
                    }
                }
            }

            override fun onKeyMoved(documentID: String, location: GeoPoint) {
                for (marker in driverMarkers){
                    val start = LatLng(location.latitude, location.longitude)
                    var end: LatLng? = null
                    val position = getPositionDriver(marker.tag.toString())

                    if(marker.tag != null){
                        if(marker.tag == documentID){

                            if(driverLocations[position].latlng != null){
                                end = driverLocations[position].latlng

                            }
                            driverLocations[position].latlng = LatLng(location.latitude, location.longitude)
                            if(end != null){
                                CarMoveAnim.carAnim(marker, end, start)
                            }
                        }
                    }
                }
            }

        })

    }

    private fun goToTripInfo() {
        if(originLatLng != null && destinationLatLng != null) {
            val intent = Intent(this, TripInfoActivity::class.java)
            intent.putExtra("origin", originName)
            intent.putExtra("destination", destinationName)
            intent.putExtra("origin_lat", originLatLng?.latitude)
            intent.putExtra("origin_lng", originLatLng?.longitude)
            intent.putExtra("destination_lat", destinationLatLng?.latitude)
            intent.putExtra("destination_lng", destinationLatLng?.longitude)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Debes seleccionar el origen y el destino", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPositionDriver(id: String): Int{
        var position = 0
        for(i in driverLocations.indices){
            if(id == driverLocations[i].id) {
                position = i
                break
            }
        }
        return position
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onCameraMove() {
        googleMap?.setOnCameraIdleListener {
            try {
                val geocoder = Geocoder(this)
                originLatLng = googleMap?.cameraPosition?.target

                if (originLatLng != null) {
                    geocoder.getFromLocation(originLatLng!!.latitude, originLatLng!!.longitude, 1, Geocoder.GeocodeListener { it ->
                        val city = it.get(0).locality
                        val country = it.get(0).countryName
                        val address = it.get(0).getAddressLine(0)
                        originName = "$address $city"
                        autocompleteOrigin?.setText("$address $city")
                    })

                }

            } catch (e: Exception) {
                Log.d("ERROR", "Mensaje error: ${e.message}")
            }
        }
    }

    private fun startGooglePlaces() {
        if(!Places.isInitialized()) {
            Places.initialize(applicationContext, resources.getString(R.string.google_maps_key))
        }
        places = Places.createClient(this)
        instanceAutocompleteOrigin()
        instanceAutocompleteDestination()
    }

    private fun limitSearch() {
        val northSide = SphericalUtil.computeOffset(myLocationLatLng, 5000.0, 0.0)
        val southSide = SphericalUtil.computeOffset(myLocationLatLng, 5000.0, 180.0)

        autocompleteOrigin?.setLocationBias(RectangularBounds.newInstance(southSide, northSide))
        autocompleteDestination?.setLocationBias(RectangularBounds.newInstance(southSide, northSide))
    }

    private fun instanceAutocompleteOrigin() {

        autocompleteOrigin = supportFragmentManager.findFragmentById(R.id.placesAutocompleteOrigin) as AutocompleteSupportFragment
        autocompleteOrigin?.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
            )
        )
        autocompleteOrigin?.setHint("Lugar de recogida")
        autocompleteOrigin?.setCountry("PE")
        autocompleteOrigin?.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                originName = place.name!!
                originLatLng = place.latLng!!
                Log.e("PLACES Origin", "Adress: $originName")
                Log.e("PLACES Origin", "LAT: ${originLatLng?.latitude}")
                Log.e("PLACES Origin", "LNG: ${originLatLng?.longitude}")

            }

            override fun onError(status: Status) {
                Log.e("PLACES Origin", status.toString())
            }
        })
    }

    private fun instanceAutocompleteDestination() {
        autocompleteDestination = supportFragmentManager.findFragmentById(R.id.placesAutocompleteDestination) as AutocompleteSupportFragment
        autocompleteDestination?.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
            )
        )
        autocompleteDestination?.setHint("Destino")
        autocompleteDestination?.setCountry("PE")
        autocompleteDestination?.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                destinationName = place.name!!
                destinationLatLng = place.latLng!!
                Log.e("PLACES Destination", "Adress: $destinationName")
                Log.e("PLACES Destination", "LAT: ${destinationLatLng?.latitude}")
                Log.e("PLACES Destination", "LNG: ${destinationLatLng?.longitude}")

            }

            override fun onError(status: Status) {
                Log.e("PLACES Destination", status.toString())
            }
        })
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        easyWayLocation?.endUpdates()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        onCameraMove()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        googleMap?.isMyLocationEnabled = false
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
        TODO("Not yet implemented")
    }

    override fun currentLocation(location: Location) {
        myLocationLatLng = LatLng(location.latitude, location.longitude) // LAT Y LONG DE LA POSICION ACTUAL

        if (!isLocationEnabled) { // UNA SOLA VEZ
            isLocationEnabled = true
            googleMap?.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.builder().target(myLocationLatLng!!).zoom(15f).build()
            ))
            getNearbyDrivers()
            limitSearch()
        }

    }

    override fun locationCancelled() {
        TODO("Not yet implemented")
    }
}