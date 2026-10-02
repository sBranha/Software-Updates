from pathlib import Path
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/NikonBluetoothActivity.java')
s=p.read_text()
old='''            String controller=p.getString("nikon_bt_controller_name","");if(controller.isEmpty()){controller=controllerName();p.edit().putString("nikon_bt_controller_name",controller).apply();}\n            write(idChr,controller.getBytes(java.nio.charset.StandardCharsets.US_ASCII));runOnUiThread(()->append("Writing controller name: "+controller));'''
new='''            String controller=p.getString("nikon_bt_controller_name","");if(controller.isEmpty()){controller=controllerName();p.edit().putString("nikon_bt_controller_name",controller).apply();}\n            final String controllerFinal=controller;\n            write(idChr,controllerFinal.getBytes(java.nio.charset.StandardCharsets.US_ASCII));runOnUiThread(()->append("Writing controller name: "+controllerFinal));'''
if old not in s: raise SystemExit('Bluetooth controller-name patch target not found')
s=s.replace(old,new,1)
p.write_text(s)
