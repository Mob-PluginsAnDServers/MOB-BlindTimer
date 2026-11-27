#!/usr/bin/env python3
"""MOB Blind Timer - wersja na komputer (Windows). Mysz + zbindowany klawisz/przycisk myszy."""
import json, math, os, random, sys, time

import pygame

# Bez globalnego podsłuchu klawiszy (antywirusy go oznaczają jako keylogger).
pk = pm = None


def res(*p):
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, "assets", *p)


def cfg_path():
    d = os.environ.get("APPDATA") or os.path.expanduser("~/.config")
    d = os.path.join(d, "MOB-BlindTimer")
    os.makedirs(d, exist_ok=True)
    return os.path.join(d, "settings.json")


DEFAULTS = {"bind": "key:space", "edit": False, "target": 1.0, "tol": 1,
            "safe": False, "anim": True, "sound": True}


def load_cfg():
    c = dict(DEFAULTS)
    try:
        with open(cfg_path(), encoding="utf-8") as f:
            c.update(json.load(f))
    except Exception:
        pass
    return c


def save_cfg(c):
    try:
        with open(cfg_path(), "w", encoding="utf-8") as f:
            json.dump(c, f)
    except Exception:
        pass


# ---------- logika (bez UI) ----------
ZERO, RUNNING, STOPPED = 0, 1, 2
DOUBLE = 0.4


class Core:
    def __init__(self, cfg):
        self.cfg = cfg
        self.state = ZERO
        self.t0 = 0.0
        self.stopped = 0.0
        self.last_tap = 0.0
        self.result = None  # None / "hit" / "miss"
        self.diff = 0

    @staticmethod
    def cs(sec):
        return int(sec * 100 + 1e-9)  # obcięcie do setnych

    def target_cs(self):
        return int(round(float(self.cfg["target"]) * 100))

    def tol(self):
        return max(0, min(50, int(self.cfg["tol"])))

    def press(self, now=None):
        """Zwraca 'start' | 'stop' | 'resume' | 'reset'."""
        now = time.perf_counter() if now is None else now
        if self.state == ZERO:
            self.t0, self.state, self.result = now, RUNNING, None
            return "start"
        if self.state == RUNNING:
            if not self.cfg["safe"] and self.last_tap and now - self.last_tap <= DOUBLE:
                self.state, self.last_tap, self.result = ZERO, 0.0, None
                return "reset"
            self.stopped = now - self.t0
            self.state, self.last_tap = STOPPED, 0.0
            if self.cfg["edit"]:
                self.diff = self.cs(self.stopped) - self.target_cs()
                self.result = "hit" if abs(self.diff) <= self.tol() else "miss"
            return "stop"
        # STOPPED
        if self.cfg["safe"]:
            self.state, self.result = ZERO, None
            return "reset"
        self.t0 = now - self.stopped
        self.last_tap, self.state, self.result = now, RUNNING, None
        return "resume"


def fmt(sec):
    c = Core.cs(sec)
    cs, s = c % 100, c // 100
    m, s = divmod(s, 60)
    return f"{m}:{s:02d}.{cs:02d}" if m else f"{s}.{cs:02d} s"


# ---------- bindowanie ----------
def pg_name(ev):
    if ev.type == pygame.KEYDOWN:
        return "key:" + pygame.key.name(ev.key)
    if ev.type == pygame.MOUSEBUTTONDOWN and ev.button in (2, 3, 6, 7):
        return {2: "mouse:middle", 3: "mouse:right", 6: "mouse:x1", 7: "mouse:x2"}[ev.button]
    return None


def pretty(b):
    k, _, v = b.partition(":")
    names = {"middle": "środkowy przycisk myszy", "right": "prawy przycisk myszy",
             "x1": "boczny przycisk myszy 1", "x2": "boczny przycisk myszy 2"}
    return names.get(v, v) if k == "mouse" else "klawisz " + v.upper()


def pynput_name(k):
    try:
        if isinstance(k, pk.KeyCode):
            return "key:" + (k.char or "").lower() if k.char else None
        return "key:" + k.name.replace("_l", "").replace("_r", "") if k.name else None
    except Exception:
        return None


# ---------- UI ----------
W, H = 520, 780
BG = (13, 13, 18)
GREEN, RED, AMBER, GREY = (0, 255, 163), (255, 77, 109), (255, 194, 77), (138, 138, 153)


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


class App:
    def __init__(self):
        pygame.init()
        try:
            pygame.mixer.init()
        except Exception:
            pass
        self.screen = pygame.display.set_mode((W, H), pygame.RESIZABLE | pygame.SCALED)
        pygame.display.set_caption("MOB Blind Timer")
        try:
            pygame.display.set_icon(pygame.image.load(res("skin_body.png")))
        except Exception:
            pass
        F = lambda n, s: pygame.font.Font(res("fonts", n), s)
        self.f = {s: F("DejaVuSans-Bold.ttf", s) for s in (14, 16, 20, 26, 40, 58)}
        self.fr = {s: F("DejaVuSans.ttf", s) for s in (13, 15, 18)}
        self.cfg = load_cfg()
        self.core = Core(self.cfg)
        self.clock = pygame.time.Clock()
        self.logo = pygame.image.load(res("splash_logo.png")).convert_alpha()
        self.body = pygame.image.load(res("skin_body.png")).convert_alpha()
        self.arm = pygame.image.load(res("skin_arm.png")).convert_alpha()
        self.frames = []
        with open(res("math_frames.txt"), encoding="utf-8") as fh:
            for ln in fh:
                p = ln.rstrip("\n").split("\t")
                if len(p) >= 5:
                    self.frames.append((p[0], hexc(p[1]), hexc(p[2]), int(p[3]), int(p[4])))
        self.frame_total = sum(x[4] for x in self.frames) / 1000.0
        self.sounds = {}
        for n in ("hit_song", "fail_buzzer"):
            try:
                self.sounds[n] = pygame.mixer.Sound(res(n + ".mp3"))
            except Exception:
                pass
        self.settings = False
        self.binding = False
        self.editing = None  # "target" / "tol"
        self.buf = ""
        self.fx = None  # ("win"/"fail", start)
        self.pending = []  # naciśnięcia z globalnego hooka
        self.start_time = time.time()
        self.msg = ""
        self.hotspots = {}
        self.start_hooks()

    # --- globalne hooki (działa też gdy okno nie jest aktywne) ---
    def start_hooks(self):
        self.listeners = []
        if pk is None:
            return

        def kd(k):
            n = pynput_name(k)
            if n and not pygame.key.get_focused():
                self.pending.append(n)

        def mc(x, y, b, pressed):
            if pressed and not pygame.key.get_focused():
                m = {pm.Button.middle: "mouse:middle", pm.Button.right: "mouse:right"}.get(b)
                if m is None:
                    m = {"x1": "mouse:x1", "x2": "mouse:x2", "button8": "mouse:x1",
                         "button9": "mouse:x2"}.get(getattr(b, "name", ""))
                if m:
                    self.pending.append(m)

        try:
            for L in (pk.Listener(on_press=kd), pm.Listener(on_click=mc)):
                L.daemon = True
                L.start()
                self.listeners.append(L)
        except Exception:
            pass

    # --- akcje ---
    def press(self):
        act = self.core.press()
        if act in ("start", "reset", "resume"):
            self.stop_audio()
            self.fx = None
        elif act == "stop" and self.cfg["edit"] and self.cfg["sound"]:
            r = self.core.result
            self.fx = ("win" if r == "hit" else "fail", time.time())
            if r == "hit" and "hit_song" in self.sounds:
                self.sounds["hit_song"].play()
            if r == "miss" and "fail_buzzer" in self.sounds:
                self.sounds["fail_buzzer"].play()
        elif act == "stop" and self.cfg["edit"]:
            self.fx = ("win" if self.core.result == "hit" else "fail", time.time())

    def stop_audio(self):
        try:
            pygame.mixer.stop()
        except Exception:
            pass

    def commit_edit(self):
        try:
            v = float(self.buf.replace(",", "."))
            if self.editing == "target":
                self.cfg["target"] = round(max(0.01, min(3599.0, v)), 2)
            else:
                self.cfg["tol"] = max(0, min(50, int(v)))
        except ValueError:
            pass
        self.editing = None
        save_cfg(self.cfg)

    # --- rysowanie ---
    def text(self, s, font, col, center=None, left=None, top=None):
        im = font.render(s, True, col)
        r = im.get_rect()
        if center:
            r.center = center
        if left is not None:
            r.left, r.centery = left, top
        self.screen.blit(im, r)
        return r

    def draw_main(self):
        s, c = self.screen, self.core
        s.fill(BG)
        status = {ZERO: "BLIND TIMER", RUNNING: "LICZYSZ…", STOPPED: "TWÓJ CZAS"}[c.state]
        self.text(status, self.f[16], GREY, (W // 2, 50))
        if c.state == ZERO:
            big = "0.00 s"
        elif c.state == STOPPED:
            big = fmt(c.stopped)
        else:
            big = ""
        self.text(big, self.f[58], GREEN, (W // 2, 150))
        if c.state == STOPPED and self.cfg["edit"] and c.result:
            col = GREEN if c.result == "hit" else RED
            d = c.diff
            sign = "+" if d > 0 else ""
            self.text(("TRAFIONE!  " if c.result == "hit" else "pudło  ") + f"{sign}{d / 100:.2f} s",
                      self.f[20], col, (W // 2, 205))
            self.text(f"cel: {c.target_cs() / 100:.2f} s  (±{c.tol() / 100:.2f})", self.fr[15], GREY,
                      (W // 2, 235))
        cx, cy, r = W // 2, 410, 135
        col, label = {ZERO: (GREEN, "START"), RUNNING: (RED, "STOP"),
                      STOPPED: (AMBER, "SAFE RESET" if self.cfg["safe"] else "RESET")}[c.state]
        if c.state == STOPPED and self.cfg["safe"]:
            label = "RESET"
        pygame.draw.circle(s, col, (cx, cy), r)
        self.text(label, self.f[40], BG, (cx, cy))
        self.hotspots["btn"] = (cx, cy, r)
        hints = {
            ZERO: "Naciśnij START (przycisk albo " + pretty(self.cfg["bind"]) + ").\nCzasu nie zobaczysz do końca.",
            RUNNING: "Czas jest ukryty",
            STOPPED: ("RESET: czas znika, nowa runda" if self.cfg["safe"] else
                      "1 klik: czas znika i liczy dalej\n2 szybkie kliki: wyzeruj do 0")}[c.state]
        for i, ln in enumerate(hints.split("\n")):
            self.text(ln, self.fr[15], GREY, (W // 2, 600 + i * 24))
        self.text("by BenioYouTube", self.fr[13], (90, 90, 105), (W // 2, H - 24))
        g = pygame.Rect(W - 56, 12, 44, 44)
        pygame.draw.rect(s, (30, 30, 40), g, border_radius=10)
        self.text("⚙", self.f[26], GREY, g.center)
        self.hotspots["gear"] = g

    def toggle(self, key, label, y):
        s = self.screen
        self.text(label, self.fr[15], (230, 230, 240), left=36, top=y)
        r = pygame.Rect(W - 36 - 62, y - 16, 62, 32)
        on = self.cfg[key]
        pygame.draw.rect(s, GREEN if on else (60, 60, 72), r, border_radius=16)
        pygame.draw.circle(s, (255, 255, 255) if on else (150, 150, 160),
                           (r.right - 16 if on else r.left + 16, r.centery), 12)
        self.hotspots[key] = r

    def field(self, key, label, y, val):
        self.text(label, self.fr[15], (230, 230, 240), left=36, top=y)
        r = pygame.Rect(W - 36 - 110, y - 18, 110, 36)
        pygame.draw.rect(s := self.screen, (30, 30, 40), r, border_radius=8)
        pygame.draw.rect(s, GREEN if self.editing == key else (70, 70, 85), r, 2, border_radius=8)
        shown = (self.buf + "|") if self.editing == key else val
        self.text(shown, self.fr[18], (255, 255, 255), r.center)
        self.hotspots[key] = r

    def draw_settings(self):
        s = self.screen
        s.fill(BG)
        self.text("USTAWIENIA", self.f[20], GREEN, (W // 2, 44))
        b = pygame.Rect(36, 90, W - 72, 52)
        pygame.draw.rect(s, (30, 30, 40), b, border_radius=10)
        pygame.draw.rect(s, AMBER if self.binding else (70, 70, 85), b, 2, border_radius=10)
        t = "Naciśnij klawisz / przycisk myszy…" if self.binding else \
            "Przycisk timera: " + pretty(self.cfg["bind"])
        self.text(t, self.fr[15], (255, 255, 255), b.center)
        self.hotspots["bind"] = b
        self.text("kliknij i naciśnij dowolny klawisz albo boczny/środkowy przycisk myszy",
                  self.fr[13], GREY, (W // 2, 162))
        self.toggle("edit", "Edit mode (cel czasowy)", 215)
        self.field("target", "Cel (sekundy)", 275, f"{float(self.cfg['target']):.2f}")
        self.field("tol", "Tolerancja (setne s)", 335, str(self.cfg["tol"]))
        tl = int(self.cfg["tol"])
        self.text(f"trafienie w ±{tl / 100:.2f} s od celu (0 = tylko dokładnie)",
                  self.fr[13], GREY, (W // 2, 375))
        self.toggle("safe", "Tryb bezpieczny (bez wznawiania)", 430)
        self.toggle("anim", "Animacje (konfetti/matma/błąd)", 490)
        self.toggle("sound", "Dźwięki (piosenka / buzzer)", 550)
        y = pygame.Rect(36, H - 110, W - 72, 52)
        pygame.draw.rect(s, GREEN, y, border_radius=10)
        self.text("GOTOWE", self.f[20], BG, y.center)
        self.hotspots["done"] = y
        self.text(f"Zapis: {cfg_path()}", self.fr[13], (90, 90, 105), (W // 2, H - 30))

    def draw_fx(self):
        if not self.fx or not self.cfg["anim"]:
            return
        kind, t0 = self.fx
        el = time.time() - t0
        s = self.screen
        if kind == "win":
            if el > self.frame_total + 0.5:
                return
            t = el % self.frame_total
            acc = 0.0
            for txt, bg, fg, size, ms in self.frames:
                acc += ms / 1000.0
                if t < acc:
                    break
            s.fill(bg)
            fnt = pygame.font.Font(res("fonts", "DejaVuSerif.ttf"), int(size * 1.2))
            jx = random.uniform(-4, 4)
            r = fnt.render(txt, True, fg)
            if r.get_width() > W - 20:
                k = (W - 20) / r.get_width()
                r = pygame.transform.smoothscale(r, (int(r.get_width() * k), int(r.get_height() * k)))
            s.blit(r, r.get_rect(center=(W // 2 + jx, H // 2)))
            self.text("kliknij żeby zamknąć", self.fr[13], fg, (W // 2, H - 24))
        else:
            if el > 1.6:
                return
            a = max(0, 1 - max(0, el - 0.6) / 0.9)
            ov = pygame.Surface((W, H), pygame.SRCALPHA)
            ov.fill((255, 23, 68, int(0x66 * a)))
            s.blit(ov, (0, 0))
            k = min(1.0, 0.2 + 0.8 * min(1, el / 0.45))
            k = 1 + 3.0 * ((k - 1) ** 3 + (k - 1) ** 2) if k < 1 else 1
            im = pygame.font.Font(res("fonts", "DejaVuSans-Bold.ttf"), max(8, int(150 * min(1.15, max(0.2, k))))).render("✖", True, (255, 255, 255))
            im.set_alpha(int(255 * a))
            s.blit(im, im.get_rect(center=(W // 2, H // 2)))

    def draw_splash(self, el):
        s = self.screen
        s.fill(BG)
        a = min(1, el / 0.6)
        lg = pygame.transform.smoothscale(self.logo, (int(200 * (0.7 + 0.3 * a)),) * 2)
        lg.set_alpha(int(255 * a))
        s.blit(lg, lg.get_rect(center=(W // 2, 190)))
        self.text("BLIND TIMER", self.f[26], GREEN, (W // 2, 330))
        sk = pygame.transform.scale(self.body, (128, 256))
        arm = pygame.transform.scale(self.arm, (128, 256))
        ang = 0 if el < 0.8 else 35 * math.sin((el - 0.8) * 9) - 20
        piv = (int(0.875 * 128), int(0.28 * 256))
        pad = pygame.Surface((256, 400), pygame.SRCALPHA)
        pad.blit(sk, (64, 40))
        ar = pygame.transform.rotozoom(arm, ang, 1)
        off = pygame.math.Vector2(arm.get_width() / 2 - piv[0], arm.get_height() / 2 - piv[1]).rotate(-ang)
        c = (64 + piv[0] + off.x * -1 + (arm.get_width() / 2 - piv[0]) * 0, 0)
        # obrót wokół barku
        center = pygame.math.Vector2(64 + piv[0], 40 + piv[1]) + \
            pygame.math.Vector2(arm.get_width() / 2 - piv[0], arm.get_height() / 2 - piv[1]).rotate(-ang)
        pad.blit(ar, ar.get_rect(center=(center.x, center.y)))
        pad.set_alpha(int(255 * min(1, max(0, (el - 0.3) / 0.5))))
        s.blit(pad, pad.get_rect(center=(W // 2, 520)))
        self.text("by BenioYouTube", self.f[16], GREY, (W // 2, 700))

    # --- pętla ---
    def handle(self, ev):
        if ev.type == pygame.QUIT:
            return False
        if time.time() - self.start_time < 2.4:
            return True
        if self.binding:
            n = pg_name(ev)
            if n and not (ev.type == pygame.KEYDOWN and ev.key == pygame.K_ESCAPE):
                self.cfg["bind"] = n
                self.binding = False
                save_cfg(self.cfg)
            elif ev.type == pygame.KEYDOWN and ev.key == pygame.K_ESCAPE:
                self.binding = False
            elif ev.type == pygame.MOUSEBUTTONDOWN and ev.button == 1 and \
                    not self.hotspots["bind"].collidepoint(ev.pos):
                self.binding = False
            return True
        if self.settings:
            if self.editing and ev.type == pygame.KEYDOWN:
                if ev.key in (pygame.K_RETURN, pygame.K_KP_ENTER):
                    self.commit_edit()
                elif ev.key == pygame.K_BACKSPACE:
                    self.buf = self.buf[:-1]
                elif ev.unicode and ev.unicode in "0123456789.,":
                    self.buf += ev.unicode
            elif ev.type == pygame.MOUSEBUTTONDOWN and ev.button == 1:
                if self.editing:
                    self.commit_edit()
                h = self.hotspots
                if h["done"].collidepoint(ev.pos):
                    self.settings = False
                    save_cfg(self.cfg)
                elif h["bind"].collidepoint(ev.pos):
                    self.binding = True
                for k in ("edit", "safe", "anim", "sound"):
                    if h[k].collidepoint(ev.pos):
                        self.cfg[k] = not self.cfg[k]
                        save_cfg(self.cfg)
                for k in ("target", "tol"):
                    if h[k].collidepoint(ev.pos):
                        self.editing, self.buf = k, ""
            elif ev.type == pygame.KEYDOWN and ev.key == pygame.K_ESCAPE:
                self.settings = False
            return True
        # ekran główny
        if ev.type == pygame.MOUSEBUTTONDOWN and ev.button == 1:
            if self.fx and self.fx[0] == "win" and self.cfg["anim"]:
                self.fx = None
                self.stop_audio()
                return True
            if self.hotspots["gear"].collidepoint(ev.pos):
                self.settings = True
                return True
            x, y, r = self.hotspots["btn"]
            if (ev.pos[0] - x) ** 2 + (ev.pos[1] - y) ** 2 <= r * r:
                self.press()
            return True
        n = pg_name(ev)
        if n and n == self.cfg["bind"]:
            self.press()
        return True

    def run(self):
        self.draw_main()
        ok = True
        while ok:
            for ev in pygame.event.get():
                ok = self.handle(ev) and ok
            while self.pending:
                n = self.pending.pop(0)
                if n == self.cfg["bind"] and not (self.settings or self.binding):
                    self.press()
                elif self.binding and pk:
                    self.cfg["bind"] = n
                    self.binding = False
                    save_cfg(self.cfg)
            el = time.time() - self.start_time
            if el < 2.4:
                self.draw_splash(el)
            elif self.settings:
                self.draw_settings()
            else:
                self.draw_main()
                self.draw_fx()
            pygame.display.flip()
            self.clock.tick(60)
        save_cfg(self.cfg)
        pygame.quit()


if __name__ == "__main__":
    App().run()
