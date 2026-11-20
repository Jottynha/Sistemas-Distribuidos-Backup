# Publicidade sincronizada

Coloque nesta pasta os arquivos de imagem que devem ser anunciados pelo middleware ativo.

Formatos aceitos: PNG, GIF, JPG, JPEG e WEBP. O middleware percorre os arquivos da pasta e anuncia um nome por vez via UDP multicast. Os clientes buscam a imagem anunciada por HTTPS em `/publicidade/{arquivo}`.

Para uma implantação com dois middlewares em hosts diferentes, mantenha os mesmos arquivos nesta pasta em A e B. Assim, depois do failover, o Middleware B também poderá servir o recurso anunciado.

O arquivo `chat-banner-exemplo.png` e somente um recurso demonstrativo; substitua-o/adicone suas próprias peças de publicidade.
