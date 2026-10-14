var info = {
  s_lat: "",
  s_lon: "",
  e_lat: "",
  e_lon: "",
};
async function showSuggestions(value, type) {
  const suggestions =
    type === "from" ? document.getElementById("fromSuggestions") : document.getElementById("toSuggestions");

  if (!value.trim()) {
    suggestions.innerHTML = "";
    return;
  }

  const headers = {
    appKey: window.TMAP_APPKEY,
  };

  const params = new URLSearchParams({
    version: 2,
    searchKeyword: value,
    resCoordType: "WGS84GEO",
    reqCoordType: "WGS84GEO",
    searchtypCd: "A",
    //    "centerLon": n_lng,
    //    "centerLat": n_lat,
    //    "radius": "0",
    count: 5,
  });

  try {
    const response = await fetch(
      `https://apis.openapi.sk.com/tmap/pois?version=1&format=json&callback=result&${params.toString()}`,
      {
        method: "GET",
        headers: headers,
      },
    );

    if (response.status != 200) {
      suggestions.innerHTML = "<div>검색 결과가 없습니다.</div>";
      return;
    }
    const data = await response.json();
    const resultpoisData = data.searchPoiInfo.pois.poi;

    let suggest = "";

    for (let k in resultpoisData) {
      const poi = resultpoisData[k];
      const name = poi.name;
      const addrName = poi.upperAddrName + " " + poi.middleAddrName + " " + poi.lowerAddrName;
      const a_lat = poi.frontLat; //noorLat-중심점, frontLat-시설물 입구, pnsLat-보행자 입구
      const a_lon = poi.frontLon;

      suggest += `<div onclick='selectSuggestion("${name}", "${type}", "${a_lat}", "${a_lon}")'>
            <i class='fas fa-map-marker-alt'></i>
            <span>${name}</span>
            <small style='margin-left:auto;'>${addrName}</small>
        </div>`;
    }
    suggestions.innerHTML = suggest;
  } catch (error) {
    console.error("API 호출 중 오류 발생:", error);
  }
}

function selectSuggestion(place, type, lat, lon) {
  document.getElementById(type + "Input").value = place;
  document.getElementById(type + "Suggestions").innerHTML = "";
  console.log(lon, lat);
  if (type == "from") {
    info.s_lat = lat;
    info.s_lon = lon;
  } //arr1[0] = [lat, lon]; }//setStart(lat, lon); }
  if (type == "to") {
    info.e_lat = lat;
    info.e_lon = lon;
  } //arr1[1] = [lat, lon]; }//setEnd(lat, lon); }
}

function swapInputs() {
  const fromInput = document.getElementById("fromInput");
  const toInput = document.getElementById("toInput");
  const temp = fromInput.value;
  fromInput.value = toInput.value;
  toInput.value = temp;

  var temp_s = [info.s_lat, info.s_lon];
  info.s_lat = info.e_lat;
  info.s_lon = info.e_lon;
  info.e_lat = temp_s[0];
  info.e_lon = temp_s[1];
  localStorage.setItem("info", JSON.stringify(info));
}

function showRouteResults() {
  const from = document.getElementById("fromInput").value;
  const to = document.getElementById("toInput").value;
  if (!from || !to) {
    alert("출발지와 도착지를 모두 입력해주세요.");
    return;
  }
  document.getElementById("routeResults").style.display = "block";
  console.log(info);
  localStorage.setItem("info", JSON.stringify(info));
  //publicRoute();
}
