var map, lat, lng, now, marker;
var n_lat, n_lng;
var markers = [], arr = []; //시작 도착 마커 배열
function initTmap() {
        map = new Tmapv2.Map("map_div", {
        center: new Tmapv2.LatLng(37.56520450, 126.98702028), // 지도 초기 좌표
        width: "100%",
        height: "100%",
        zoom: 16
    });
    //carRoute();
    //publicTest();
    publicRoute();
    // 현위치 마커
//    if (navigator.geolocation) {
//    navigator.geolocation.getCurrentPosition(
//        function(position) {
//            n_lat = position.coords.latitude;
//            n_lng = position.coords.longitude;
//
//            // 팝업 생성
//            // let content = "<div style=' position: relative; border-bottom: 1px solid #dcdcdc; line-height: 18px; padding: 0 3px 2px 0;'>"
//            // 		+ "<div style='font-size: 12px; line-height: 15px;'>"
//            // 		+ "<span style='display: inline-block; width: 4px; height: 5px; background-image: url(/resources/images/common/icon_blet.png); vertical-align: middle; margin-right: 5px;'></span>현재위치"
//            // 		+ "</div>" + "</div>";
//
//            // 현위치 마커 생성
//            // let marker = new Tmapv2.Marker({
//            // 	position : new Tmapv2.LatLng(n_lat,n_lng),
//            // 	map : map
//            // });
//
//            // 마커 설명
//            // InfoWindow = new Tmapv2.InfoWindow({
//            // 	position : new Tmapv2.LatLng(n_lat,n_lng),
//            // 	content : content,
//            // 	type : 2,
//            // 	map : map
//            // });
//
//            // 현위치
//            now = new Tmapv2.LatLng(n_lat,n_lng);
//            map.setCenter(now);
//            map.setZoom(15);
//        });
//    }

//    map.addListener("contextmenu", onContextmenu);
//    map.addListener("click", onClick);
}

// 현위치 이동
function Move(){
    map.setCenter(now); // 지도의 중심 좌표를 설정합니다.
}

// 모든 마커 제거
function removeMarkers() {
    for (let i = 0; i < markers.length; i++) {
        markers[i].setMap(null);
    }
    markers = [];
}

// 현재 마커 제거
function removeMarker() {
    marker.setMap(null);
    marker = null;
}
// Marker 생성 | color(b,g,r,w) size(b,m,s) name(0~9/a~z)
// function setMarker(i_lat, i_lon, c, s, n)
function setMarker(infoObj) {
    if(!map) {alert('no'); return;}

    let i_marker = new Tmapv2.Marker({
        position : new Tmapv2.LatLng(infoObj.lat, infoObj.lng),
        icon : infoObj.markerImage,
        map : map
    });
    if(infoObj.pointType == 'start') {
        arr[0] = i_marker;
        resultMarkerArr.push(i_marker);
    }
    else if(infoObj.pointType == 'end') {
        arr[1] = i_marker;
        resultMarkerArr.push(i_marker);
    }
    else if(infoObj.poingType == 'P') {
        // 포인터 마커 안나오게 설정
        i_marker.setMap(null);
        resultMarkerArr.push(i_marker);
    }

}

// 좌클릭
function onClick(e) {
    hideContextMenu();
    if (marker != null) { removeMarker(); }
}

// 우클릭
function onContextmenu(e) {
    hideContextMenu();
    removeMarkers();
    lat = e.latLng.lat();
    lng = e.latLng.lng();

    marker = new Tmapv2.Marker({
        position: new Tmapv2.LatLng(lat,lng),
        icon: `https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_r_s_simple.png`,
        map: map
    });
    markers.push(marker);
    // var lngLat = e.lngLat;
    // console.log(lngLat);

    const menu = document.getElementById('contextMenu');
    menu.style.left = e.pageX + "px";
    menu.style.top = e.pageY + "px";
    menu.style.display = "block";
}

function setStart(s_lat, s_lng) {
    if (markers.length > 0) { removeMarker(); }
    if (arr[0]) { arr[0].setMap(null); arr[0] = null; }

    if (s_lat == 0 && s_lng == 0) { s_lat = lat; s_lng = lng;}
    var infoObj = {
        markerImage : 'https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_b_m_s.png',
        lng : s_lng,
        lat : s_lat,
        pointType : 'start'
    };
    setMarker(infoObj);
//    setMarker(s_lat, s_lng, 'b', 'm', 's');
//    s_marker = new Tmapv2.Marker({
//        position: new Tmapv2.LatLng(s_lat,s_lng),
//        icon: `https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_b_m_s.png`,
//        title: 'start',
//        //map: map
//    });
    hideContextMenu();
}

function setWaypoint() {
    alert("경유지로 설정되었습니다.");
    hideContextMenu();
}

function setEnd(e_lat, e_lng) {
    if (marker) { removeMarker(); }
    if (arr[1]) { arr[1].setMap(null); arr[1] = null; }

    if (e_lat == 0 && e_lng == 0) { e_lat = lat; e_lng = lng;}
    var infoObj = {
        markerImage : 'https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_r_m_e.png',
        lng : e_lng,
        lat : e_lat,
        pointType : 'end'
    };
    setMarker(infoObj);
//    setMarker(e_lat, e_lng, 'r', 'm', 'e');
//    e_marker = new Tmapv2.Marker({
//        position: new Tmapv2.LatLng(lat,lng),
//        icon: `https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_r_m_e.png`,
//        title: 'end',
//        //map: map
//    });
    hideContextMenu();
}

function hideContextMenu() {
    const contextMenu = document.getElementById("contextMenu");
    contextMenu.style.display = "none";
    // $("#contextMenu").hide(); // 메뉴 숨기기
}