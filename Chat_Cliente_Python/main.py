from __future__ import annotations

import tkinter as tk
from tkinter import messagebox

from chat_client.config import load_config
from chat_client.gui import ChatWindow


def main() -> None:
    root = tk.Tk()
    try:
        config = load_config()
        ChatWindow(root, config)
    except Exception as error:
        root.withdraw()
        messagebox.showerror("Erro de configuracao", str(error))
        root.destroy()
        return
    root.mainloop()


if __name__ == "__main__":
    main()
