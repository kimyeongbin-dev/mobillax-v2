var drawInfoArr = [];
var drawInfoArr2 = [];

var chktraffic = [];
var resultdrawArr = [];
var resultMarkerArr = [];

async function carRoute() {
    resettingMap();
    let searchOption = 0; //$("#selectLevel").val();
    const type = new URLSearchParams(window.location.search).get("type");
    if(type == 'time') { searchOption = 2; }
    else if(type == 'distance') { searchOption = 10; }
    else if(type == 'transfer') { searchOption = 0; }
    const trafficInfochk = "Y";
    const headers = {
        "Content-Type": "application/json",
        "appKey": window.TMAP_APPKEY
    };

    const {s_lat, s_lon, e_lat, e_lon} = JSON.parse(localStorage.getItem('info'));
    // localStorage.removeItem('info');
    const params ={
        "startX" : Number(s_lon),
        "startY" : Number(s_lat),
        "endX" :  Number(e_lon),
        "endY" :  Number(e_lat),
        "reqCoordType" : "WGS84GEO",
        "resCoordType" : "EPSG3857",
        "searchOption" : searchOption,
        "trafficInfo" : 'Y'               //Y or N
        // "passList" : 경유지
    };

//    var marker_s = new Tmapv2.Marker({
//        position : new Tmapv2.LatLng(Number(s_lon),
//                Number(s_lat)),
//        icon : "https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_b_m_s.png",
//        iconSize : new Tmapv2.Size(24, 38),
//        map : map
//    });
//
//    //도착
//    var marker_e = new Tmapv2.Marker({
//        position : new Tmapv2.LatLng(Number(e_lon),
//                Number(e_lat)),
//        icon : "https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_r_m_e.png",
//        iconSize : new Tmapv2.Size(24, 38),
//        map : map
//    });

    try {
        const response = await fetch(`https://apis.openapi.sk.com/tmap/routes?version=1&format=json&callback=result&appKey=${window.TMAP_APPKEY}`, {
            method: "POST",
            headers: headers,
            body: JSON.stringify(params)
        });

        // if (!response.ok) { throw new Error(`HTTP Error! Status: ${response.status}`); }

        const data = await response.json();
        const carRouteData = data.features;
        console.log(carRouteData[0]);

		const tDistance = (carRouteData[0].properties.totalDistance / 1000).toFixed(1) + "km";
		var tTime = "";
		if(Math.floor((carRouteData[0].properties.totalTime / 60) / 60)) {
		    tTime = Math.floor((carRouteData[0].properties.totalTime / 60) / 60).toFixed(0) + "시 "
                    		   + ((carRouteData[0].properties.totalTime / 60) % 60).toFixed(0) + "분";
		}
        else { tTime = (carRouteData[0].properties.totalTime / 60).toFixed(0) + "분"; }
		const tFare = "총 요금 정보: " + carRouteData[0].properties.totalFare + "원";
		const taxiFare = carRouteData[0].properties.taxiFare.toLocaleString() + "원"; // 택시 요금

        document.querySelector('.summary-item:nth-child(1) strong').textContent = tTime;
        document.querySelector('.summary-item:nth-child(2) strong').textContent = taxiFare;
        document.querySelector('.summary-item:nth-child(3) strong').textContent = "0";
        // 위 내용 삽입
        // document.getElementById("routeResult").textContent = tDistance + tTime + tFare + taxiFare;

        const routeBounds = new Tmapv2.LatLngBounds();
        var marker_s = new Tmapv2.LatLng(Number(s_lat), Number(s_lon));
        var marker_e = new Tmapv2.LatLng(Number(e_lat), Number(e_lon));
        routeBounds.extend(marker_s);
        routeBounds.extend(marker_e);

        if (trafficInfochk == "Y") {
            for (var i in carRouteData) { //for문 [S]
                var geometry = carRouteData[i].geometry;
                var properties = carRouteData[i].properties;

                if (geometry.type == "LineString") {
                    //교통 정보도 담음
                    chktraffic.push(geometry.traffic);
                    var sectionInfos = [];
                    var trafficArr = geometry.traffic;

                    for (var j in geometry.coordinates) {
                        // 경로들의 결과값들을 포인트 객체로 변환
                        var latlng = new Tmapv2.Point(
                                geometry.coordinates[j][0],
                                geometry.coordinates[j][1]);
                        // 포인트 객체를 받아 좌표값으로 변환
                        var convertPoint = new Tmapv2.Projection.convertEPSG3857ToWGS84GEO(latlng);
                        sectionInfos.push(convertPoint);
                    }
                    drawLine(sectionInfos, trafficArr);
                }
                else {
                    var markerImg = "";
                    var pType = "";

                    if (properties.pointType == "S") { //출발지 마커
                        markerImg = "https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_b_m_s.png";
                        pType = "S";

                    } else if (properties.pointType == "E") { //도착지 마커
                        markerImg = "https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_r_m_e.png";
                        pType = "E";
                    }
                    else { //각 포인트 마커
                        markerImg = "http://topopen.tmap.co.kr/imgs/point.png";
                        pType = "P";
                        continue;
                    }

                    // 경로들의 결과값들을 포인트 객체로 변환
                    var latlon = new Tmapv2.Point(
                            geometry.coordinates[0],
                            geometry.coordinates[1]);
                    // 포인트 객체를 받아 좌표값으로 다시 변환
                    var convertPoint = new Tmapv2.Projection.convertEPSG3857ToWGS84GEO(latlon);

                    var routeInfoObj = {
                        markerImage : markerImg,
                        lng : convertPoint._lng,
                        lat : convertPoint._lat,
                        pointType : pType
                    };
                    // 마커 추가
                    setMarker(routeInfoObj);
                }
            }//for문 [E]
        } else {

            for ( var i in carRouteData) { //for문 [S]
                var geometry = carRouteData[i].geometry;
                var properties = carRouteData[i].properties;

                if (geometry.type == "LineString") {
                    for ( var j in geometry.coordinates) {
                        // 경로들의 결과값들을 포인트 객체로 변환
                        var latlng = new Tmapv2.Point(
                                geometry.coordinates[j][0],
                                geometry.coordinates[j][1]);
                        // 포인트 객체를 받아 좌표값으로 변환
                        var convertPoint = new Tmapv2.Projection.convertEPSG3857ToWGS84GEO(latlng);
                        // 포인트객체의 정보로 좌표값 변환 객체로 저장
                        var convertChange = new Tmapv2.LatLng(
                                convertPoint._lat,
                                convertPoint._lng);
                        // 배열에 담기
                        drawInfoArr.push(convertChange);
                    }
                    drawLine(drawInfoArr, "0");
                }
                else {
                    var markerImg = "";
                    var pType = "";

                    if (properties.pointType == "S") { //출발지 마커
                        markerImg = "https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_b_m_s.png";
                        pType = "S";
                    } else if (properties.pointType == "E") { //도착지 마커
                        markerImg = "https://tmapapi.tmapmobility.com/upload/tmap/marker/pin_r_m_e.png";
                        pType = "E";
                    } else { //각 포인트 마커
                        markerImg = "http://topopen.tmap.co.kr/imgs/point.png";
                        pType = "P";
                        continue;
                    }

                    // 경로들의 결과값들을 포인트 객체로 변환
                    var latlon = new Tmapv2.Point(
                            geometry.coordinates[0],
                            geometry.coordinates[1]);
                    // 포인트 객체를 받아 좌표값으로 다시 변환
                    var convertPoint = new Tmapv2.Projection.convertEPSG3857ToWGS84GEO(latlon);

                    var routeInfoObj = {
                        markerImage : markerImg,
                        lng : convertPoint._lng,
                        lat : convertPoint._lat,
                        pointType : pType
                    };
                    // Marker 추가
                    setMarker(routeInfoObj);
                }
            }//for문 [E]
        }
    map.panToBounds(routeBounds);

    } catch (error) {
        console.error("car API 호출 중 오류 발생:", error);
    }

}

// 경로 마커 지우기
function resettingMap() {
    // 기존 start, end 마커 제거
//    arr[0].setMap(null);
//    arr[1].setMap(null);

    if(resultMarkerArr.length > 0) {
        for(var i = 0; i < resultMarkerArr.length; i++ ) {
            resultMarkerArr[i].setMap(null);
        }
    }

    if(resultdrawArr.length > 0) {
        for(var i = 0; i < resultdrawArr.length; i++) {
            resultdrawArr[i].setMap(null);
        }
    }

    chktraffic = [];
    drawInfoArr = [];
    resultMarkerArr = [];
    resultdrawArr = [];
}

//라인그리기
function drawLine(arrPoint, traffic) {
    var polyline_;

    if (chktraffic.length != 0) {

        // 교통정보 혼잡도를 체크
        // strokeColor는 교통 정보상황에 다라서 변화
        // traffic :  0-정보없음, 1-원활, 2-서행, 3-지체, 4-정체  (black, green, yellow, orange, red)

        var lineColor = "";

        if (traffic != "0") {
            if (traffic.length == 0) { //length가 0인것은 교통정보가 없으므로 검은색으로 표시

                lineColor = "#06050D";
                //라인그리기[S]
                polyline_ = new Tmapv2.Polyline({
                    path : arrPoint,
                    strokeColor : lineColor,
                    strokeWeight : 6,
                    map : map
                });
                resultdrawArr.push(polyline_);
                //라인그리기[E]
            } else { //교통정보가 있음

                if (traffic[0][0] != 0) { //교통정보 시작인덱스가 0이 아닌경우
                    var trafficObject = "";
                    var tInfo = [];

                    for (var z = 0; z < traffic.length; z++) {
                        trafficObject = {
                            "startIndex" : traffic[z][0],
                            "endIndex" : traffic[z][1],
                            "trafficIndex" : traffic[z][2],
                        };
                        tInfo.push(trafficObject)
                    }

                    var noInformationPoint = [];

                    for (var p = 0; p < tInfo[0].startIndex; p++) {
                        noInformationPoint.push(arrPoint[p]);
                    }

                    //라인그리기[S]
                    polyline_ = new Tmapv2.Polyline({
                        path : noInformationPoint,
                        strokeColor : "#06050D",
                        strokeWeight : 6,
                        map : map
                    });
                    //라인그리기[E]
                    resultdrawArr.push(polyline_);

                    for (var x = 0; x < tInfo.length; x++) {
                        var sectionPoint = []; //구간선언

                        for (var y = tInfo[x].startIndex; y <= tInfo[x].endIndex; y++) {
                            sectionPoint.push(arrPoint[y]);
                        }

                        if (tInfo[x].trafficIndex == 0) {
                            lineColor = "#06050D";
                        } else if (tInfo[x].trafficIndex == 1) {
                            lineColor = "#61AB25";
                        } else if (tInfo[x].trafficIndex == 2) {
                            lineColor = "#FFFF00";
                        } else if (tInfo[x].trafficIndex == 3) {
                            lineColor = "#E87506";
                        } else if (tInfo[x].trafficIndex == 4) {
                            lineColor = "#D61125";
                        }

                        //라인그리기[S]
                        polyline_ = new Tmapv2.Polyline({
                            path : sectionPoint,
                            strokeColor : lineColor,
                            strokeWeight : 6,
                            map : map
                        });
                        //라인그리기[E]
                        resultdrawArr.push(polyline_);
                    }
                } else { //0부터 시작하는 경우

                    var trafficObject = "";
                    var tInfo = [];

                    for (var z = 0; z < traffic.length; z++) {
                        trafficObject = {
                            "startIndex" : traffic[z][0],
                            "endIndex" : traffic[z][1],
                            "trafficIndex" : traffic[z][2],
                        };
                        tInfo.push(trafficObject)
                    }

                    for (var x = 0; x < tInfo.length; x++) {
                        var sectionPoint = []; //구간선언

                        for (var y = tInfo[x].startIndex; y <= tInfo[x].endIndex; y++) {
                            sectionPoint.push(arrPoint[y]);
                        }

                        if (tInfo[x].trafficIndex == 0) {
                            lineColor = "#06050D";
                        } else if (tInfo[x].trafficIndex == 1) {
                            lineColor = "#61AB25";
                        } else if (tInfo[x].trafficIndex == 2) {
                            lineColor = "#FFFF00";
                        } else if (tInfo[x].trafficIndex == 3) {
                            lineColor = "#E87506";
                        } else if (tInfo[x].trafficIndex == 4) {
                            lineColor = "#D61125";
                        }

                        //라인그리기[S]
                        polyline_ = new Tmapv2.Polyline({
                            path : sectionPoint,
                            strokeColor : lineColor,
                            strokeWeight : 6,
                            map : map
                        });
                        //라인그리기[E]
                        resultdrawArr.push(polyline_);
                    }
                }
            }
        } else {

        }
    } else {
        polyline_ = new Tmapv2.Polyline({
            path : arrPoint,
            strokeColor : "#DD0000",
            strokeWeight : 6,
            map : map
        });
        resultdrawArr.push(polyline_);
    }

}

function addComma(num) {
    var regexp = /\B(?=(\d{3})+(?!\d))/g;
    return num.toString().replace(regexp, ',');
}