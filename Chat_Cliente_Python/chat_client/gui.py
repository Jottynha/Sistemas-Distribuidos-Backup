from __future__ import annotations

import io
import queue
import re
import threading
import tkinter as tk
from html.parser import HTMLParser
from tkinter import messagebox, ttk
from tkinter.scrolledtext import ScrolledText
from typing import Any, Callable

try:
    from PIL import Image, ImageTk, ImageSequence
except ImportError:  # mensagem amigavel caso a dependencia ainda nao tenha sido instalada
    Image = None
    ImageTk = None
    ImageSequence = None

from .config import ClientConfig
from .protocol import (
    create_history_request,
    create_sticker_message,
    create_text_message,
)
from .transport import TLSJsonTransport
from .advertising import MulticastAdvertisementListener


_IMAGE_PLACEHOLDER = "[carregando imagem...]"

_EMOJIS = (
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
    "😊", "😇", "🙂", "😉", "😍", "🥰", "😘", "😎",
    "🤔", "🤨", "😴", "😭", "😡", "🤯", "🥳", "🤗",
    "👍", "👎", "👏", "🙏", "🤝", "💪", "❤️", "💔",
    "🔥", "🎉", "✅", "❌", "⭐", "🚀", "💯", "✨",
)


class _RichHistoryParser(HTMLParser):
    """Converte HTML do middleware em texto Tk formatado e imagens embutidas."""

    BLOCK_TAGS = {
        "address", "article", "blockquote", "div", "footer", "h1", "h2",
        "h3", "h4", "header", "li", "ol", "p", "section", "table",
        "tr", "ul",
    }
    VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "wbr"}

    def __init__(
        self,
        widget: ScrolledText,
        generation: int,
        image_callback: Callable[[str, str, str, int], None],
    ) -> None:
        super().__init__(convert_charrefs=True)
        self.widget = widget
        self.generation = generation
        self.image_callback = image_callback
        self.stack: list[tuple[str, str | None]] = []
        self.active_tags: list[str] = []
        self.style_cache: dict[tuple[tuple[str, str], ...], str] = {}
        self.counter = 0
        self.hidden_tags: list[str] = []
        self.in_pre = False

    def _insert(self, value: str) -> None:
        if value:
            self.widget.insert("end-1c", value, tuple(self.active_tags))

    def _newline(self) -> None:
        current = self.widget.get("1.0", "end-1c")
        if current and not current.endswith("\n"):
            self._insert("\n")

    @staticmethod
    def _parse_style(style_text: str) -> dict[str, str]:
        result: dict[str, str] = {}
        for declaration in (style_text or "").split(";"):
            if ":" not in declaration:
                continue
            key, value = declaration.split(":", 1)
            result[key.strip().lower()] = value.strip()
        return result

    @staticmethod
    def _safe_color(value: str) -> str | None:
        value = value.strip()
        if re.fullmatch(r"#[0-9a-fA-F]{3,8}", value):
            return value
        if re.fullmatch(r"[a-zA-Z][a-zA-Z0-9 -]{0,30}", value):
            return value
        return None

    def _font_size(self, value: str) -> int | None:
        match = re.match(r"^\s*(\d+(?:\.\d+)?)\s*(px|pt)?\s*$", value, re.I)
        if not match:
            return None
        number = float(match.group(1))
        unit = (match.group(2) or "pt").lower()
        points = number * 0.75 if unit == "px" else number
        return max(8, min(28, round(points)))

    def _configure_tag(self, tag: str, attrs: dict[str, str | None]) -> str | None:
        styles = self._parse_style(attrs.get("style") or "")
        options: dict[str, Any] = {}
        size = 10
        weight = "normal"
        slant = "roman"
        underline = False
        overstrike = False

        if tag in {"b", "strong", "th"}:
            weight = "bold"
        elif tag in {"i", "em", "cite", "dfn"}:
            slant = "italic"
        elif tag == "u":
            underline = True
        elif tag in {"s", "del", "strike"}:
            overstrike = True
        elif tag == "h1":
            size, weight = 18, "bold"
        elif tag == "h2":
            size, weight = 15, "bold"
        elif tag == "h3":
            size, weight = 13, "bold"
        elif tag == "h4":
            size, weight = 11, "bold"
        elif tag == "small":
            size = 9
        elif tag == "a":
            options["foreground"] = "#2563eb"
            underline = True

        if tag == "font" and attrs.get("color"):
            color = self._safe_color(attrs["color"] or "")
            if color:
                options["foreground"] = color
        if tag == "font" and attrs.get("size"):
            try:
                size = max(8, min(28, int(attrs["size"])))
            except ValueError:
                pass

        if "color" in styles:
            color = self._safe_color(styles["color"])
            if color:
                options["foreground"] = color
        if "background-color" in styles:
            color = self._safe_color(styles["background-color"])
            if color:
                options["background"] = color
        if "font-size" in styles:
            parsed_size = self._font_size(styles["font-size"])
            if parsed_size:
                size = parsed_size
        if styles.get("font-weight", "").lower() in {"bold", "600", "700", "800", "900"}:
            weight = "bold"
        if styles.get("font-style", "").lower() == "italic":
            slant = "italic"
        decoration = styles.get("text-decoration", "").lower()
        underline = underline or "underline" in decoration
        overstrike = overstrike or "line-through" in decoration
        if styles.get("text-align", "").lower() in {"center", "right", "left"}:
            options["justify"] = styles["text-align"].lower()

        options["font"] = ("Segoe UI", size, weight, slant)
        if underline:
            options["underline"] = True
        if overstrike:
            options["overstrike"] = True

        # Tag vazio nao precisa ser aplicado a elementos sem estilo especial.
        default_options = {"font": ("Segoe UI", 10, "normal", "roman")}
        if options == default_options and tag not in self.BLOCK_TAGS:
            return None

        key = tuple(sorted((str(k), str(v)) for k, v in options.items()))
        cached = self.style_cache.get(key)
        if cached:
            return cached
        name = f"html_style_{len(self.style_cache) + 1}"
        self.widget.tag_configure(name, **options)
        self.style_cache[key] = name
        return name

    def handle_starttag(self, tag: str, attrs_list: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        attrs = dict(attrs_list)

        if tag in {"script", "style", "head"}:
            self.hidden_tags.append(tag)
            return
        if self.hidden_tags:
            return

        if tag == "pre":
            self.in_pre = True

        if tag in self.BLOCK_TAGS:
            self._newline()

        if tag == "br":
            self._insert("\n")
            return
        if tag == "hr":
            self._newline()
            self._insert("─" * 48 + "\n")
            return
        if tag == "li":
            self._insert("• ")

        if tag == "img":
            source = (attrs.get("src") or "").strip()
            alt = (attrs.get("alt") or "Imagem").strip()
            if not source:
                self._insert(f"[{alt}]")
                return
            self.counter += 1
            mark = f"html_image_{self.generation}_{self.counter}"
            position = self.widget.index("end-1c")
            self._insert(_IMAGE_PLACEHOLDER)
            self.widget.mark_set(mark, position)
            self.widget.mark_gravity(mark, tk.LEFT)
            self.image_callback(source, alt, mark, self.generation)
            return

        style_name = self._configure_tag(tag, attrs)
        if tag not in self.VOID_TAGS:
            self.stack.append((tag, style_name))
            if style_name:
                self.active_tags.append(style_name)

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if self.hidden_tags:
            if tag == self.hidden_tags[-1]:
                self.hidden_tags.pop()
            return

        if tag == "pre":
            self.in_pre = False

        for index in range(len(self.stack) - 1, -1, -1):
            stacked_tag, style_name = self.stack[index]
            if stacked_tag == tag:
                del self.stack[index:]
                self.active_tags = [
                    name for _stacked_tag, name in self.stack if name is not None
                ]
                break

        if tag in self.BLOCK_TAGS:
            self._newline()

    def handle_data(self, data: str) -> None:
        if self.hidden_tags:
            return
        if not self.in_pre:
            data = re.sub(r"\s+", " ", data)
        self._insert(data)


class ChatWindow:
    def __init__(self, root: tk.Tk, config: ClientConfig):
        self.root = root
        self.config = config
        self.transport = TLSJsonTransport(
            config.host,
            config.port,
            config.ca_file,
            config.timeout,
            config.backup_hosts,
            config.web_port,
        )
        self.events: queue.Queue[tuple[str, Any, Exception | None]] = queue.Queue()
        self.history_pending = False
        self.send_pending = False
        self.pending_send_kind: str | None = None
        self.stickers_pending = False
        self.last_history: str | None = None
        self.history_generation = 0
        self.image_marks: set[str] = set()
        self.image_refs: list[Any] = []
        self.advertisement_listener: MulticastAdvertisementListener | None = None
        self.advertisement_image_ref: Any = None
        self.advertisement_frames: list[tuple[Any, int]] = []
        self.advertisement_frame_index = 0
        self.advertisement_animation_after_id: str | None = None
        self.closed = False

        root.title("Chat | Cliente Python")
        root.geometry("960x720")
        root.minsize(700, 540)
        try:
            ttk.Style(root).theme_use("clam")
        except tk.TclError:
            pass

        self._build_interface()
        self.advertisement_listener = MulticastAdvertisementListener(
            config.multicast_group, config.multicast_port, self._on_advertisement
        )
        self.advertisement_listener.start()
        self.root.after(100, self._process_events)
        self.root.after(500, self._refresh_history)
        self.root.after(config.history_interval * 1000, self._schedule_refresh)
        self.root.protocol("WM_DELETE_WINDOW", self._close)

    
    def _build_interface(self) -> None:
        style = ttk.Style(self.root)
        style.configure("Title.TLabel", font=("Segoe UI", 17, "bold"))
        style.configure("Subtle.TLabel", foreground="#5b6472")
        style.configure(
            "Send.TButton",
            font=("Segoe UI", 10, "bold"),
            padding=(16, 8),
        )

        outer = ttk.Frame(self.root, padding=12)
        outer.pack(fill=tk.BOTH, expand=True)

        # A linha do histórico recebe todo o espaço variável.
        # A área de composição permanece reservada na parte inferior.
        outer.columnconfigure(0, weight=1)
        outer.rowconfigure(3, weight=1)

        # Cabeçalho
        header = ttk.Frame(outer)
        header.grid(row=0, column=0, sticky="ew", pady=(0, 10))

        title_area = ttk.Frame(header)
        title_area.pack(side=tk.LEFT, fill=tk.X, expand=True)

        ttk.Label(
            title_area,
            text="Chat",
            style="Title.TLabel",
        ).pack(anchor=tk.W)

        ttk.Label(
            title_area,
            text=(
                f"Cliente Python  •  Middleware TLS "
                f"{self.config.host}:{self.config.port}"
            ),
            style="Subtle.TLabel",
        ).pack(anchor=tk.W, pady=(2, 0))

        ttk.Button(
            header,
            text="Atualizar histórico",
            command=self._refresh_history,
        ).pack(side=tk.RIGHT)

        # Identificação
        identity = ttk.LabelFrame(
            outer,
            text="Identificação",
            padding=(10, 8),
        )
        identity.grid(row=1, column=0, sticky="ew", pady=(0, 8))

        identity.columnconfigure(0, weight=1)
        identity.columnconfigure(1, weight=1)

        ttk.Label(
            identity,
            text="Seu nome",
        ).grid(row=0, column=0, sticky=tk.W)

        self.sender_var = tk.StringVar(value="Python")

        ttk.Entry(
            identity,
            textvariable=self.sender_var,
            width=22,
        ).grid(
            row=1,
            column=0,
            sticky="ew",
            padx=(0, 14),
            pady=(3, 0),
        )

        ttk.Label(
            identity,
            text="Destinatário (opcional)",
        ).grid(row=0, column=1, sticky=tk.W)

        self.recipient_var = tk.StringVar(value="")

        ttk.Entry(
            identity,
            textvariable=self.recipient_var,
            width=24,
        ).grid(
            row=1,
            column=1,
            sticky="ew",
            padx=(0, 4),
            pady=(3, 0),
        )

        # Area exclusiva para publicidade sincronizada por multicast.
        advertisement_frame = ttk.LabelFrame(outer, text="Publicidade", padding=6)
        advertisement_frame.grid(row=2, column=0, sticky="ew", pady=(0, 8))
        self.advertisement_label = ttk.Label(
            advertisement_frame,
            text="Aguardando anuncio multicast...",
            anchor=tk.CENTER,
            justify=tk.CENTER,
        )
        self.advertisement_label.pack(fill=tk.X, expand=True)
        advertisement_frame.configure(height=100)
        advertisement_frame.pack_propagate(False)

        # Histórico: ocupa somente a área flexível.
        history_frame = ttk.LabelFrame(
            outer,
            text="Mensagens",
            padding=8,
        )
        history_frame.grid(
            row=3,
            column=0,
            sticky="nsew",
            pady=(0, 8),
        )

        history_frame.rowconfigure(0, weight=1)
        history_frame.columnconfigure(0, weight=1)

        self.history = ScrolledText(
            history_frame,
            wrap=tk.WORD,
            state=tk.DISABLED,
            font=("Segoe UI", 10),
            background="#ffffff",
            foreground="#202733",
            relief=tk.FLAT,
            borderwidth=0,
            padx=12,
            pady=10,
            spacing1=2,
            spacing3=3,
        )
        self.history.pack(fill=tk.BOTH, expand=True)

        self.history.tag_configure(
            "image_error",
            foreground="#8a8f98",
            font=("Segoe UI", 9, "italic"),
        )

        # Área de composição: fica sempre abaixo do histórico.
        composer = ttk.LabelFrame(
            outer,
            text="Nova mensagem",
            padding=8,
        )
        composer.grid(row=4, column=0, sticky="ew")

        composer_row = ttk.Frame(composer)
        composer_row.pack(fill=tk.X)

        self.message_entry = ScrolledText(
            composer_row,
            height=3,
            wrap=tk.WORD,
            font=("Segoe UI", 10),
            relief=tk.SOLID,
            borderwidth=1,
            padx=8,
            pady=6,
        )
        self.message_entry.pack(
            side=tk.LEFT,
            fill=tk.X,
            expand=True,
            padx=(0, 10),
        )

        self.message_entry.bind(
            "<Control-Return>",
            self._send_with_shortcut,
        )

        self.send_button = ttk.Button(
            composer_row,
            text="Enviar",
            style="Send.TButton",
            command=self._send_message,
        )
        self.send_button.pack(side=tk.RIGHT, fill=tk.Y)

        # Botões de emojis e figurinhas.
        media_row = ttk.Frame(composer)
        media_row.pack(fill=tk.X, pady=(7, 0))

        self.emoji_button = ttk.Button(
            media_row,
            text="Emoji",
            command=self._show_emoji_picker,
        )
        self.emoji_button.pack(side=tk.LEFT, padx=(0, 6))

        self.sticker_button = ttk.Button(
            media_row,
            text="Figurinhas",
            command=self._load_stickers,
        )
        self.sticker_button.pack(side=tk.LEFT)

        ttk.Label(
            composer,
            text="Enter cria uma nova linha  •  Ctrl + Enter envia",
            style="Subtle.TLabel",
        ).pack(anchor=tk.W, pady=(5, 0))

        # Status
        self.status_var = tk.StringVar(
            value="Aguardando conexão com o middleware..."
        )

        ttk.Label(
            outer,
            textvariable=self.status_var,
            style="Subtle.TLabel",
        ).grid(
            row=5,
            column=0,
            sticky=tk.W,
            pady=(8, 0),
        )


    def _show_emoji_picker(self) -> None:
        """Abre uma paleta simples; o emoji e inserido como texto Unicode."""
        popup = tk.Toplevel(self.root)
        popup.title("Escolher emoji")
        popup.transient(self.root)
        popup.resizable(False, False)

        container = ttk.Frame(popup, padding=10)
        container.pack(fill=tk.BOTH, expand=True)
        for index, emoji in enumerate(_EMOJIS):
            button = tk.Button(
                container,
                text=emoji,
                font=("Segoe UI Emoji", 17),
                width=3,
                relief=tk.FLAT,
                command=lambda value=emoji: self._insert_emoji(value, popup),
            )
            button.grid(row=index // 8, column=index % 8, padx=2, pady=2)

        popup.update_idletasks()
        x = self.root.winfo_rootx() + 30
        y = self.root.winfo_rooty() + self.root.winfo_height() - popup.winfo_height() - 80
        popup.geometry(f"+{max(0, x)}+{max(0, y)}")

    def _insert_emoji(self, emoji: str, popup: tk.Toplevel) -> None:
        self.message_entry.insert(tk.INSERT, emoji)
        self.message_entry.focus_set()
        popup.destroy()

    def _on_advertisement(self, filename: str) -> None:
        if self.closed:
            return
        self._run_background(
            "advertisement",
            lambda: (filename, self.transport.fetch_image(f"/publicidade/{filename}")),
            (filename, None),
        )

    def _display_advertisement(self, filename: str, image_data: bytes | None) -> None:
        if self.advertisement_animation_after_id is not None:
            try:
                self.root.after_cancel(self.advertisement_animation_after_id)
            except tk.TclError:
                pass
            self.advertisement_animation_after_id = None

        self.advertisement_frames.clear()
        self.advertisement_frame_index = 0

        if image_data is None or Image is None or ImageTk is None:
            self.advertisement_label.configure(
                text=f"Publicidade recebida: {filename}", image=""
            )
            return

        try:
            with Image.open(io.BytesIO(image_data)) as source_image:
                is_gif = source_image.format == "GIF" and ImageSequence is not None
                frames = ImageSequence.Iterator(source_image) if is_gif else [source_image]

                for source_frame in frames:
                    rendered = source_frame.convert("RGBA")
                    rendered.thumbnail((820, 82), Image.Resampling.LANCZOS)
                    photo = ImageTk.PhotoImage(rendered, master=self.root)
                    duration = max(50, min(2000, int(source_frame.info.get("duration", 120))))
                    self.advertisement_frames.append((photo, duration))

            if not self.advertisement_frames:
                raise ValueError("A imagem de publicidade nao possui quadros validos.")

            self._show_advertisement_frame()
        except Exception as error:
            self.advertisement_label.configure(text=f"Publicidade: {filename}", image="")
            self.status_var.set(f"Nao foi possivel exibir a publicidade: {error}")

    def _show_advertisement_frame(self) -> None:
        if self.closed or not self.advertisement_frames:
            return
        photo, duration = self.advertisement_frames[self.advertisement_frame_index]
        self.advertisement_image_ref = photo
        self.advertisement_label.configure(image=photo, text="")
        self.advertisement_frame_index = (
            self.advertisement_frame_index + 1
        ) % len(self.advertisement_frames)
        if len(self.advertisement_frames) > 1:
            self.advertisement_animation_after_id = self.root.after(
                duration, self._show_advertisement_frame
            )

    def _load_stickers(self) -> None:
        if self.closed or self.stickers_pending:
            return
        self.stickers_pending = True
        self.sticker_button.configure(state=tk.DISABLED)
        self.status_var.set("Consultando as figurinhas disponíveis no middleware...")
        self._run_background(
            "stickers",
            lambda: self.transport.fetch_stickers(),
            [],
        )

    def _show_sticker_picker(self, stickers: list[dict[str, str]]) -> None:
        if not stickers:
            messagebox.showinfo(
                "Figurinhas",
                "O middleware não retornou figurinhas disponíveis.",
                parent=self.root,
            )
            return

        popup = tk.Toplevel(self.root)
        popup.title("Enviar figurinha")
        popup.transient(self.root)
        popup.geometry("420x420")
        popup.minsize(320, 280)

        container = ttk.Frame(popup, padding=12)
        container.pack(fill=tk.BOTH, expand=True)
        ttk.Label(
            container,
            text="Selecione uma figurinha para enviar ao chat:",
        ).pack(anchor=tk.W, pady=(0, 8))

        list_frame = ttk.Frame(container)
        list_frame.pack(fill=tk.BOTH, expand=True)
        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL)
        listbox = tk.Listbox(
            list_frame,
            exportselection=False,
            font=("Segoe UI", 10),
            yscrollcommand=scrollbar.set,
        )
        scrollbar.configure(command=listbox.yview)
        listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        for item in stickers:
            listbox.insert(tk.END, item["label"])
        listbox.selection_set(0)

        def send_selected(_event: tk.Event | None = None) -> None:
            selection = listbox.curselection()
            if not selection:
                messagebox.showwarning(
                    "Figurinhas", "Selecione uma figurinha.", parent=popup
                )
                return
            selected = stickers[selection[0]]
            popup.destroy()
            self._send_sticker(selected["value"])

        actions = ttk.Frame(container)
        actions.pack(fill=tk.X, pady=(10, 0))
        ttk.Button(actions, text="Cancelar", command=popup.destroy).pack(side=tk.RIGHT)
        ttk.Button(actions, text="Enviar figurinha", command=send_selected).pack(
            side=tk.RIGHT, padx=(0, 8)
        )
        listbox.bind("<Double-Button-1>", send_selected)

    def _sender(self) -> str:
        sender = self.sender_var.get().strip()
        if not sender:
            raise ValueError("Informe seu nome ou apelido.")
        if len(sender) > 20:
            raise ValueError("O nome deve ter no máximo 20 caracteres.")
        return sender

    def _run_background(self, action: str, operation, fallback: Any = None) -> None:
        def worker() -> None:
            try:
                result = operation()
                self.events.put((action, result, None))
            except Exception as error:
                self.events.put((action, fallback, error))

        threading.Thread(
            target=worker,
            name=f"python-chat-{action}",
            daemon=True,
        ).start()

    def _request_image(self, source: str, alt: str, mark: str, generation: int) -> None:
        fallback = (mark, generation, alt, None)
        self.image_marks.add(mark)
        operation = lambda: (
            mark,
            generation,
            alt,
            self.transport.fetch_image(source),
        )
        self._run_background("image", operation, fallback)

    def _render_history(self, html: str) -> None:
        self.history.configure(state=tk.NORMAL)
        for mark in tuple(self.image_marks):
            try:
                self.history.mark_unset(mark)
            except tk.TclError:
                pass
        self.image_marks.clear()
        self.history.delete("1.0", tk.END)
        self.image_refs.clear()
        self.history_generation += 1

        parser = _RichHistoryParser(
            self.history,
            self.history_generation,
            self._request_image,
        )
        try:
            parser.feed(html or "")
            parser.close()
        except Exception as error:
            self.history.delete("1.0", tk.END)
            self.history.insert(tk.END, f"Não foi possível renderizar o histórico HTML: {error}")

        if not self.history.get("1.0", "end-1c").strip():
            self.history.insert(tk.END, "Ainda não há mensagens no histórico.")
        self.history.see(tk.END)
        self.history.configure(state=tk.DISABLED)

    def _finish_image(
        self,
        mark: str,
        generation: int,
        alt: str,
        image_data: bytes | None,
        error: Exception | None,
    ) -> None:
        if generation != self.history_generation or mark not in self.history.mark_names():
            self.image_marks.discard(mark)
            return

        self.history.configure(state=tk.NORMAL)
        try:
            position = self.history.index(mark)
            end = f"{position}+{len(_IMAGE_PLACEHOLDER)}c"
            if error is not None or Image is None or ImageTk is None:
                description = alt or "imagem indisponível"
                self.history.delete(position, end)
                self.history.insert(position, f"[{description}]")
                self.history.tag_add("image_error", position, f"{position}+{len(description) + 2}c")
            else:
                with Image.open(io.BytesIO(image_data or b"")) as source_image:
                    rendered = source_image.convert("RGBA")
                    rendered.thumbnail((280, 220), Image.Resampling.LANCZOS)
                photo = ImageTk.PhotoImage(rendered, master=self.root)
                self.history.delete(position, end)
                self.history.image_create(position, image=photo, align=tk.CENTER)
                self.image_refs.append(photo)
            self.history.mark_unset(mark)
        except Exception:
            try:
                position = self.history.index(mark)
                self.history.delete(position, f"{position}+{len(_IMAGE_PLACEHOLDER)}c")
                self.history.insert(position, f"[{alt or 'imagem indisponível'}]", "image_error")
                self.history.mark_unset(mark)
            except tk.TclError:
                pass
        finally:
            self.image_marks.discard(mark)
            self.history.configure(state=tk.DISABLED)

    def _refresh_history(self) -> None:
        if self.closed or self.history_pending:
            return
        try:
            sender = self._sender()
        except ValueError as error:
            self.status_var.set(str(error))
            return

        self.history_pending = True
        request = create_history_request(sender)
        self._run_background("history", lambda: self.transport.request(request))

    def _schedule_refresh(self) -> None:
        if self.closed:
            return
        self._refresh_history()
        self.root.after(self.config.history_interval * 1000, self._schedule_refresh)

    def _send_with_shortcut(self, _event: tk.Event) -> str:
        self._send_message()
        return "break"

    def _send_message(self) -> None:
        if self.send_pending:
            return
        try:
            sender = self._sender()
        except ValueError as error:
            messagebox.showwarning("Identificação", str(error), parent=self.root)
            return

        content = self.message_entry.get("1.0", "end-1c").strip()
        if not content:
            return

        request = create_text_message(
            sender,
            self.recipient_var.get(),
            content,
        )
        self._queue_send(request, "text")

    def _send_sticker(self, sticker_value: str) -> None:
        if self.send_pending:
            return
        try:
            sender = self._sender()
            request = create_sticker_message(
                sender,
                self.recipient_var.get(),
                sticker_value,
            )
        except ValueError as error:
            messagebox.showwarning("Figurinhas", str(error), parent=self.root)
            return
        self._queue_send(request, "sticker")

    def _queue_send(self, request: dict[str, Any], kind: str) -> None:
        self.send_pending = True
        self.pending_send_kind = kind
        self.send_button.configure(state=tk.DISABLED)
        self.sticker_button.configure(state=tk.DISABLED)
        self.status_var.set(
            "Enviando figurinha pelo canal TLS..."
            if kind == "sticker"
            else "Enviando mensagem pelo canal TLS..."
        )
        self._run_background("send", lambda: self.transport.request(request))

    def _process_events(self) -> None:
        if self.closed:
            return
        while True:
            try:
                action, result, error = self.events.get_nowait()
            except queue.Empty:
                break

            if action == "history":
                self.history_pending = False
                if error is not None:
                    self.status_var.set(f"Falha ao consultar histórico: {error}")
                    continue

                if result.get("type") != "HISTORY_RESPONSE":
                    self.status_var.set(
                        f"Resposta inesperada ao consultar histórico: {result.get('type')}"
                    )
                    continue

                html = result.get("content", "")
                if html != self.last_history:
                    self.last_history = html
                    self._render_history(html)
                self.status_var.set("Conectado ao middleware por TLS e histórico sincronizado.")

            elif action == "send":
                self.send_pending = False
                self.send_button.configure(state=tk.NORMAL)
                self.sticker_button.configure(state=tk.NORMAL)
                sent_kind = self.pending_send_kind
                self.pending_send_kind = None
                if error is not None:
                    self.status_var.set(f"Falha no envio: {error}")
                    continue

                if result.get("type") == "ACK":
                    if sent_kind == "text":
                        self.message_entry.delete("1.0", tk.END)
                        self.status_var.set("Mensagem recebida e registrada pelo middleware.")
                    else:
                        self.status_var.set("Figurinha recebida e registrada pelo middleware.")
                    self._refresh_history()
                else:
                    self.status_var.set(
                        f"Resposta inesperada ao enviar: {result.get('type')}"
                    )

            elif action == "stickers":
                self.stickers_pending = False
                self.sticker_button.configure(state=tk.NORMAL)
                if error is not None:
                    self.status_var.set(f"Falha ao carregar figurinhas: {error}")
                    messagebox.showerror(
                        "Figurinhas",
                        "Não foi possível consultar /api/stickers no middleware.\n\n"
                        f"{error}",
                        parent=self.root,
                    )
                    continue
                self._show_sticker_picker(result or [])
                self.status_var.set("Catálogo de figurinhas carregado.")

            elif action == "advertisement":
                if error is None and result:
                    filename, image_data = result
                    self._display_advertisement(filename, image_data)

            elif action == "image":
                mark, generation, alt, image_data = result
                self._finish_image(mark, generation, alt, image_data, error)

        self.root.after(100, self._process_events)

    def _close(self) -> None:
        self.closed = True
        if self.advertisement_animation_after_id is not None:
            try:
                self.root.after_cancel(self.advertisement_animation_after_id)
            except tk.TclError:
                pass
            self.advertisement_animation_after_id = None
        if self.advertisement_listener is not None:
            self.advertisement_listener.close()
        self.root.destroy()
