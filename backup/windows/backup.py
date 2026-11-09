import hashlib,json,os,socket,struct,tkinter as tk
from tkinter import filedialog,messagebox,ttk
from pathlib import Path
from datetime import datetime
PORT=38471
def receive(root,destination,status):
    destination=Path(destination)
    if not destination.exists(): raise RuntimeError("Connect your external drive first")
    with socket.socket() as server:
        server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        server.bind(("0.0.0.0",PORT));server.listen(1);server.settimeout(120)
        status.set("Waiting for phone on port 38471");root.update()
        conn,addr=server.accept()
        with conn:
            conn.settimeout(120)
            def exact(n):
                data=b""
                while len(data)<n:
                    chunk=conn.recv(min(65536,n-len(data)))
                    if not chunk: raise EOFError("Phone disconnected")
                    data+=chunk
                return data
            meta=json.loads(exact(struct.unpack(">I",exact(4))[0]))
            safe=lambda x:"".join(c for c in x if c.isalnum() or c in "._-")[:100]
            folder=destination/"PhoneBackups"/safe(meta.get("device","Android"))/datetime.now().strftime("%Y%m%d-%H%M%S")
            folder.mkdir(parents=True)
            report=[]
            for item in meta["files"]:
                name=safe(item["name"]); category=safe(item["category"])
                length=int(item["size"])
                if length<0 or length>100*1024**3: raise ValueError("Invalid size")
                target=folder/category/name
                target.parent.mkdir(parents=True,exist_ok=True)
                digest=hashlib.sha256()
                with target.open("wb") as f:
                    remaining=length
                    while remaining:
                        block=exact(min(65536,remaining));f.write(block);digest.update(block);remaining-=len(block)
                valid=digest.hexdigest()==item["sha256"]
                report.append({"file":str(target.relative_to(folder)),"verified":valid,"bytes":length})
                status.set(f"Received {len(report)} / {len(meta['files'])}: {name}");root.update()
            (folder/"report.json").write_text(json.dumps(report,indent=2))
            status.set(f"Completed: {sum(x['verified'] for x in report)}/{len(report)} verified")
def main():
    root=tk.Tk();root.title("Wireless Phone Backup 0.1");root.geometry("560x230")
    dest=tk.StringVar();status=tk.StringVar(value="Choose your external drive backup folder")
    ttk.Label(root,text="External drive destination").pack(pady=8)
    ttk.Entry(root,textvariable=dest,width=68).pack()
    ttk.Button(root,text="Browse",command=lambda:dest.set(filedialog.askdirectory())).pack()
    def run():
        try:receive(root,dest.get(),status)
        except Exception as e:messagebox.showerror("Backup error",str(e));status.set("Backup failed")
    ttk.Button(root,text="Backup Now (wait for phone)",command=run).pack(pady=12)
    ttk.Label(root,textvariable=status,wraplength=520).pack()
    root.mainloop()
if __name__=="__main__":main()
