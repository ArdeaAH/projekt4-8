# pylint: disable=import-error

import sys

import os

import sqlite3

import numpy as np

import tkinter as tk

from tkinter import messagebox

from datetime import datetime

import cv2 

import csv

import face_recognition # AI brain for face comparison

from PIL import Image, ImageTk



# --- KONFIGURIMI I DOSJEVE ---

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

DB_NAME = os.path.join(BASE_DIR, "school_database.db")

FACES_DIR = os.path.join(BASE_DIR, "student_photos")

LOG_FILE = os.path.join(BASE_DIR, "attendance_log.csv")



# Sigurohemi që dosja e fotove ekziston

if not os.path.exists(FACES_DIR):

    os.makedirs(FACES_DIR, exist_ok=True)



def init_db():

    """Krijon tabelën për studentët në Database."""

    try:

        conn = sqlite3.connect(DB_NAME)

        cursor = conn.cursor()

        cursor.execute('''CREATE TABLE IF NOT EXISTS students 

                         (id INTEGER PRIMARY KEY AUTOINCREMENT, 

                          full_name TEXT, 

                          student_class TEXT,

                          photo_path TEXT)''')

        conn.commit()

        conn.close()

    except Exception as e:

        print(f"Gabim në Database: {e}")



def log_attendance(name, student_class):

    """Shënon prezencën në një skedar CSV për Excel."""

    now = datetime.now()

    date_str = now.strftime("%Y-%m-%d")

    time_str = now.strftime("%H:%M:%S")

    

    file_exists = os.path.isfile(LOG_FILE)

    with open(LOG_FILE, mode='a', newline='') as f:

        writer = csv.writer(f)

        if not file_exists:

            writer.writerow(["Studenti", "Klasa", "Data", "Ora"])

        writer.writerow([name, student_class, date_str, time_str])



class App:

    def __init__(self, root):

        self.root = root

        self.root.title("Student Face ID - AI Edition")

        self.root.geometry("450x700")

        self.captured_frame = None



        # --- UI ELEMENTS ---

        tk.Label(root, text="Regjistrimi i Studentëve", font=("Arial", 20, "bold")).pack(pady=15)

        

        # Emri

        tk.Label(root, text="Emër Mbiemër:", font=("Arial", 10)).pack()

        self.ent_name = tk.Entry(root, font=("Arial", 12), width=30)

        self.ent_name.pack(pady=5)



        # Klasa

        tk.Label(root, text="Klasa (p.sh. 10-A):", font=("Arial", 10)).pack()

        self.ent_class = tk.Entry(root, font=("Arial", 12), width=30)

        self.ent_class.pack(pady=5)



        self.lbl_photo_preview = tk.Label(root, text="Asnjë foto e kapur", bg="lightgrey", width=30, height=8)

        self.lbl_photo_preview.pack(pady=10)



        # Buttons

        tk.Button(root, text="📸 1. KAP FOTON", font=("Arial", 11, "bold"), 

                  command=self.take_photo, width=25, height=2).pack(pady=5)



        tk.Button(root, text="💾 2. RUAJ STUDENTIN", bg="#28a745", fg="white", 

                  font=("Arial", 11, "bold"), command=self.save_student, width=25, height=2).pack(pady=5)

        

        tk.Label(root, text="_______________________________________", fg="gray").pack(pady=10)

        

        tk.Button(root, text="🚀 HAP SKANERIN LIVE", bg="#007bff", fg="white", 

                  font=("Arial", 14, "bold"), command=self.run_scanner, width=25, height=2).pack(pady=10)



    def get_camera(self):

        cam = cv2.VideoCapture(0)

        if not cam.isOpened():

            return None

        return cam



    def update_preview(self, frame):

        cv_img = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        pil_img = Image.fromarray(cv_img)

        pil_img = pil_img.resize((200, 150), Image.Resampling.LANCZOS)

        tk_img = ImageTk.PhotoImage(image=pil_img)

        self.lbl_photo_preview.config(image=tk_img, text="")

        self.lbl_photo_preview.image = tk_img 



    def take_photo(self):

        cam = self.get_camera()

        if cam is None:

            messagebox.showerror("Gabim", "Kamera nuk u gjet!")

            return



        messagebox.showinfo("Kamera", "Shikoni kamerën.\nShtypni SPACE për të shkrepur foton.")

        while True:

            ret, frame = cam.read()

            if not ret: break

            

            cv2.imshow("Shkrepja - Shtyp SPACE", frame)

            k = cv2.waitKey(1)

            if k % 256 == 32: # Space

                self.captured_frame = frame.copy()

                self.update_preview(self.captured_frame)

                break

            elif k % 256 == 27: # Esc

                break

        

        cam.release()

        cv2.destroyAllWindows()



    def save_student(self):

        name = self.ent_name.get().strip()

        s_class = self.ent_class.get().strip()



        if not name or not s_class or self.captured_frame is None:

            messagebox.showwarning("Kujdes", "Plotësoni emrin, klasën dhe kapni një foto!")

            return



        # Ruaj foton

        filename = f"{name.replace(' ', '_')}_{datetime.now().strftime('%H%M%S')}.jpg"

        photo_path = os.path.join(FACES_DIR, filename)

        cv2.imwrite(photo_path, self.captured_frame)



        # Ruaj në Database

        conn = sqlite3.connect(DB_NAME)

        cursor = conn.cursor()

        cursor.execute("INSERT INTO students (full_name, student_class, photo_path) VALUES (?, ?, ?)", 

                       (name, s_class, photo_path))

        conn.commit()

        conn.close()



        messagebox.showinfo("Sukses", f"Studenti {name} u regjistrua!")

        self.ent_name.delete(0, tk.END)

        self.ent_class.delete(0, tk.END)

        self.lbl_photo_preview.config(image='', text="Asnjë foto e kapur")

        self.captured_frame = None



    def load_known_faces(self):

        """Ngarkon fytyrat nga databaza për skanim."""

        known_encodings = []

        known_metadata = [] # Ruajmë emrin dhe klasën

        

        conn = sqlite3.connect(DB_NAME)

        cursor = conn.cursor()

        cursor.execute("SELECT full_name, student_class, photo_path FROM students")

        rows = cursor.fetchall()

        conn.close()



        for name, s_class, path in rows:

            if os.path.exists(path):

                img = face_recognition.load_image_file(path)

                encs = face_recognition.face_encodings(img)

                if encs:

                    known_encodings.append(encs[0])

                    known_metadata.append({"name": name, "class": s_class})

        

        return known_encodings, known_metadata



    def run_scanner(self):

        known_encs, known_meta = self.load_known_faces()

        if not known_encs:

            messagebox.showwarning("Vërejtje", "Nuk ka studentë të regjistruar!")

            return



        cap = self.get_camera()

        last_logged = {} # Për të mos spamuar CSV-në



        while True:

            ret, frame = cap.read()

            if not ret: break

            

            # Përpunim më i shpejtë

            small_frame = cv2.resize(frame, (0, 0), fx=0.25, fy=0.25)

            rgb_small = cv2.cvtColor(small_frame, cv2.COLOR_BGR2RGB)

            

            face_locations = face_recognition.face_locations(rgb_small)

            face_encodings = face_recognition.face_encodings(rgb_small, face_locations)



            for (top, right, bottom, left), face_enc in zip(face_locations, face_encodings):

                matches = face_recognition.compare_faces(known_encs, face_enc, tolerance=0.5)

                name = "I panjohur"

                s_class = ""



                face_distances = face_recognition.face_distance(known_encs, face_enc)

                if len(face_distances) > 0:

                    best_match_index = np.argmin(face_distances)

                    if matches[best_match_index]:

                        name = known_meta[best_match_index]["name"]

                        s_class = known_meta[best_match_index]["class"]



                # Logimi në CSV (çdo 30 sekonda për person)

                if name != "I panjohur":

                    now = datetime.now()

                    if name not in last_logged or (now - last_logged[name]).total_seconds() > 30:

                        log_attendance(name, s_class)

                        last_logged[name] = now



                # Vizatimi në ekran

                top *= 4; right *= 4; bottom *= 4; left *= 4

                color = (0, 255, 0) if name != "I panjohur" else (0, 0, 255)

                cv2.rectangle(frame, (left, top), (right, bottom), color, 2)

                cv2.putText(frame, f"{name} ({s_class})", (left, top-10), 

                            cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2)



            cv2.imshow("Skaneri Live - Shtyp 'q' për mbyllje", frame)

            if cv2.waitKey(1) & 0xFF == ord('q'): break

                

        cap.release()

        cv2.destroyAllWindows()



if __name__ == "__main__":

    init_db()

    root = tk.Tk()

    app = App(root)

    root.mainloop()

