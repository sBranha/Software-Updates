#!/usr/bin/env python3
"""Cache NOAA's public Doppler-station catalog inside the Android app at build time.
If upstream is unavailable the app keeps its bundled sample rather than failing the build.
"""
import json
import re
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen, Request

dest=Path(__file__).parent / "app/src/main/assets/stations.js"
service="https://coast.noaa.gov/arcgis/rest/services/Hosted/WeatherRadarStations/FeatureServer/0/query"
params={
 "where":"1=1","outFields":"siteidentifier,sitename,radartype",
 "returnGeometry":"true","outSR":"4326","f":"geojson"}
url=service+"?"+urlencode(params)
try:
 req=Request(url,headers={"User-Agent":"NewsWave-Live-Android/0.2 (NOAA public radar stations)"})
 with urlopen(req,timeout=35) as resp:
  data=json.load(resp)
 if "features" not in data:
  raise ValueError("No GeoJSON features: "+str(data)[:300])
 stations={}
 for feature in data["features"]:
  prop=feature.get("properties") or {}
  code=str(prop.get("siteidentifier") or "").strip().upper()
  if not re.fullmatch(r"(?:[KP][A-Z0-9]{3}|TJUA)",code):
   continue
  coords=(feature.get("geometry") or {}).get("coordinates") or []
  if len(coords)<2:continue
  lon,lat=float(coords[0]),float(coords[1])
  if abs(lat)>90 or abs(lon)>180:continue
  stations[code]={"id":code,"name":str(prop.get("sitename") or code).strip(),"lat":round(lat,5),"lon":round(lon,5)}
 if len(stations)<100:
  raise ValueError("Only %d stations in NOAA inventory; retaining bundled fallback" % len(stations))
 sorted_stations=sorted(stations.values(), key=lambda x:x["id"])
 dest.write_text("window.RADAR_STATIONS="+json.dumps(sorted_stations,separators=(',',':'),ensure_ascii=False)+";\n",encoding="utf-8")
 print("Bundled %d nationwide NOAA radar stations" % len(sorted_stations))
 print("KHTX:",stations.get("KHTX"))
except Exception as e:
 print("WARNING: NOAA station inventory unavailable. Retaining bundled fallback:",repr(e))
