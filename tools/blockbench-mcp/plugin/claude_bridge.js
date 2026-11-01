/**
 * Claude Bridge – Blockbench-Plugin
 *
 * Verbindet Blockbench mit dem MCP-Server aus tools/blockbench-mcp/server.js.
 * Das Plugin baut eine WebSocket-Verbindung zu ws://127.0.0.1:<Port> auf (Standard 9333)
 * und führt die Befehle aus, die Claude über MCP schickt: Projekt anlegen, Knochen und
 * Würfel setzen, Texturen laden, Animationen anlegen, Screenshots machen, exportieren.
 *
 * Funktioniert in der Desktop- und in der Web-Version von Blockbench.
 * Installation: Datei > Plugins > „Load Plugin from File“ und diese Datei wählen.
 */
(function () {
    const PLUGIN_ID = 'claude_bridge';
    const DEFAULT_PORT = 9333;
    let socket = null;
    let reconnectTimer = null;
    let stopped = false;
    let statusAction = null;

    // ------------------------------------------------------------------ Hilfen

    function requireProject() {
        if (!Project) throw new Error('Kein Projekt offen. Zuerst new_project aufrufen.');
    }

    function vec(value, fallback) {
        if (value === undefined || value === null) return fallback.slice();
        if (!Array.isArray(value) || value.length !== 3) throw new Error('Erwartet [x, y, z], bekommen: ' + JSON.stringify(value));
        return value.map(Number);
    }

    function findGroup(name) {
        if (!name) return null;
        const group = Group.all.find(g => g.name === name);
        if (!group) throw new Error('Knochen nicht gefunden: ' + name);
        return group;
    }

    function findNode(name) {
        const node = Group.all.find(g => g.name === name) || Outliner.elements.find(e => e.name === name);
        if (!node) throw new Error('Element nicht gefunden: ' + name);
        return node;
    }

    function edit(label, aspects, fn) {
        Undo.initEdit(aspects);
        let result;
        try {
            result = fn();
        } finally {
            Undo.finishEdit(label);
        }
        Canvas.updateAll();
        return result;
    }

    function describeNode(node) {
        if (node instanceof Group) {
            return {
                type: 'bone', name: node.name, origin: node.origin, rotation: node.rotation,
                children: node.children.map(describeNode),
            };
        }
        if (node instanceof Cube) {
            return {
                type: 'cube', name: node.name, from: node.from, to: node.to,
                uv_offset: node.uv_offset, inflate: node.inflate, rotation: node.rotation,
                origin: node.origin, mirror_uv: node.mirror_uv,
            };
        }
        return { type: node.type, name: node.name };
    }

    function uvLayout() {
        // Wo jede Würfelseite auf der Textur liegt (in Texturpixeln), damit Claude gezielt malen kann.
        return Cube.all.map(cube => {
            const faces = {};
            for (const dir of ['north', 'east', 'south', 'west', 'up', 'down']) {
                const face = cube.faces[dir];
                if (face && face.uv) faces[dir] = face.uv.map(n => Math.round(n * 100) / 100);
            }
            return { name: cube.name, faces };
        });
    }

    // ------------------------------------------------------------------ Befehle

    const commands = {
        status() {
            return {
                blockbench: Blockbench.version,
                project: Project ? {
                    name: Project.name, format: Project.format && Project.format.id,
                    texture_size: [Project.texture_width, Project.texture_height], box_uv: Project.box_uv,
                    bones: Group.all.length, cubes: Cube.all.length, textures: Texture.all.length,
                    animations: Animation.all.length,
                } : null,
                formats: Object.keys(Formats),
            };
        },

        new_project({ format = 'modded_entity', name = 'model', texture_width = 64, texture_height = 64, box_uv = true }) {
            if (!Formats[format]) throw new Error('Unbekanntes Format: ' + format + '. Möglich: ' + Object.keys(Formats).join(', '));
            newProject(Formats[format]);
            Project.name = name;
            Project.texture_width = texture_width;
            Project.texture_height = texture_height;
            if (Format.optional_box_uv || format === 'modded_entity') Project.box_uv = box_uv;
            return commands.status();
        },

        add_bone({ name, parent, origin, rotation }) {
            requireProject();
            if (!name) throw new Error('name fehlt');
            if (Group.all.some(g => g.name === name)) throw new Error('Knochen existiert schon: ' + name);
            const parentGroup = findGroup(parent);
            return edit('Claude: Knochen ' + name, { outliner: true }, () => {
                const group = new Group({ name, origin: vec(origin, [0, 0, 0]), rotation: vec(rotation, [0, 0, 0]) });
                group.addTo(parentGroup || undefined);
                group.init();
                return describeNode(group);
            });
        },

        add_cube({ name, bone, from, to, uv_offset, inflate = 0, rotation, origin, mirror_uv = false }) {
            requireProject();
            if (!name) throw new Error('name fehlt');
            const group = findGroup(bone);
            const cube = edit('Claude: Würfel ' + name, { outliner: true, elements: [] }, () => {
                const c = new Cube({
                    name,
                    from: vec(from, [0, 0, 0]),
                    to: vec(to, [1, 1, 1]),
                    inflate: Number(inflate) || 0,
                    rotation: vec(rotation, [0, 0, 0]),
                    origin: origin ? vec(origin, [0, 0, 0]) : (group ? group.origin.slice() : [0, 0, 0]),
                    mirror_uv: !!mirror_uv,
                });
                if (uv_offset) c.uv_offset = [Number(uv_offset[0]), Number(uv_offset[1])];
                c.addTo(group || undefined);
                c.init();
                if (Texture.all.length) c.applyTexture(Texture.all[0], true);
                return c;
            });
            return describeNode(cube);
        },

        update_element({ name, ...props }) {
            requireProject();
            const node = findNode(name);
            return edit('Claude: ändern ' + name, { outliner: true, elements: node instanceof Cube ? [node] : [] }, () => {
                for (const key of ['from', 'to', 'origin', 'rotation']) {
                    if (props[key] !== undefined) node[key] = vec(props[key], [0, 0, 0]);
                }
                if (props.uv_offset !== undefined && node instanceof Cube) node.uv_offset = props.uv_offset.map(Number);
                if (props.inflate !== undefined && node instanceof Cube) node.inflate = Number(props.inflate);
                if (props.mirror_uv !== undefined && node instanceof Cube) node.mirror_uv = !!props.mirror_uv;
                if (props.new_name) node.name = props.new_name;
                if (props.parent !== undefined) node.addTo(findGroup(props.parent) || undefined);
                if (node instanceof Cube) node.preview_controller.updateAll(node);
                return describeNode(node);
            });
        },

        delete_element({ name }) {
            requireProject();
            const node = findNode(name);
            edit('Claude: löschen ' + name, { outliner: true, elements: node instanceof Cube ? [node] : [] }, () => node.remove());
            return { deleted: name };
        },

        outline() {
            requireProject();
            return { outliner: Outliner.root.map(describeNode), uv_layout: uvLayout() };
        },

        set_texture({ name = 'texture', png_base64 }) {
            requireProject();
            if (!png_base64) throw new Error('png_base64 fehlt');
            return new Promise((resolve, reject) => {
                const existing = Texture.all.find(t => t.name === name);
                const texture = existing || new Texture({ name });
                const img = new Image();
                img.onload = () => {
                    texture.fromDataURL('data:image/png;base64,' + png_base64);
                    if (!existing) texture.add(false);
                    Cube.all.forEach(c => c.applyTexture(texture, true));
                    Canvas.updateAll();
                    resolve({ texture: name, width: img.width, height: img.height });
                };
                img.onerror = () => reject(new Error('PNG konnte nicht gelesen werden'));
                img.src = 'data:image/png;base64,' + png_base64;
            });
        },

        get_texture({ name }) {
            requireProject();
            const texture = name ? Texture.all.find(t => t.name === name) : Texture.all[0];
            if (!texture) throw new Error('Keine Textur vorhanden');
            return { name: texture.name, png_base64: texture.getDataURL().split(',')[1] };
        },

        add_animation({ name, length = 1, loop = 'loop', keyframes = [] }) {
            requireProject();
            if (!Format.animation_mode) throw new Error('Dieses Format unterstützt keine Animationen');
            const animation = new Animation({ name, length: Number(length), loop, snapping: 20 }).add(false);
            for (const kf of keyframes) {
                const group = findGroup(kf.bone);
                const animator = animation.getBoneAnimator(group);
                const [x, y, z] = vec(kf.value, [0, 0, 0]);
                animator.addKeyframe({
                    channel: kf.channel || 'rotation',
                    time: Number(kf.time) || 0,
                    interpolation: kf.interpolation || 'linear',
                    data_points: [{ x, y, z }],
                });
            }
            return { animation: name, length: animation.length, keyframes: keyframes.length };
        },

        screenshot({ view = 'iso', width = 512, height = 512, animation, time = 0 }) {
            requireProject();
            const presets = {
                iso: { position: [-40, 30, -40], target: [0, 10, 0] },
                front: { position: [0, 12, -60], target: [0, 12, 0] },
                side: { position: [60, 12, 0], target: [0, 12, 0] },
                back: { position: [0, 12, 60], target: [0, 12, 0] },
                top: { position: [0, 80, 0.01], target: [0, 0, 0] },
            };
            const preset = typeof view === 'object' ? view : presets[view];
            if (!preset) throw new Error('Unbekannte Ansicht: ' + view + '. Möglich: ' + Object.keys(presets).join(', '));
            // Animationen zeigt Blockbench nur im Animationsmodus; danach zurück in den vorherigen Modus.
            const previousMode = Modes.selected && Modes.selected.id;
            if (animation) {
                const anim = Animation.all.find(a => a.name === animation);
                if (!anim) throw new Error('Animation nicht gefunden: ' + animation);
                Modes.options.animate.select();
                anim.select();
                Timeline.setTime(Number(time));
                Animator.preview();
            }
            const preview = Preview.selected || Preview.all[0];
            preview.loadAnglePreset(preset);
            if (!animation) Canvas.updateAll();
            return new Promise(resolve => {
                preview.screenshot({ width, height, crop: false }, dataUrl => {
                    if (animation && previousMode && previousMode !== 'animate') Modes.options[previousMode].select();
                    resolve({ view: typeof view === 'object' ? 'custom' : view, png_base64: String(dataUrl).split(',')[1] });
                });
            });
        },

        export({ format = 'bbmodel' }) {
            requireProject();
            if (format === 'bbmodel') {
                return { format, content: JSON.stringify(Codecs.project.compile({ raw: true }), null, 2) };
            }
            const codec = Codecs[format];
            if (!codec) throw new Error('Unbekanntes Exportformat: ' + format + '. Möglich: bbmodel, ' + Object.keys(Codecs).join(', '));
            const compiled = codec.compile();
            return { format, content: typeof compiled === 'string' ? compiled : JSON.stringify(compiled, null, 2) };
        },

        import_bbmodel({ content }) {
            const model = typeof content === 'string' ? JSON.parse(content) : content;
            const format = Formats[(model.meta && model.meta.model_format) || 'free'] || Formats.free;
            newProject(format);
            Codecs.project.load(model, { path: '' });
            return commands.status();
        },

        eval_js({ code }) {
            // Doppelt gesichert: Der Server bietet das Werkzeug nur mit BLOCKBENCH_MCP_ALLOW_EVAL=1 an,
            // und Blockbench führt es nur aus, wenn es hier ausdrücklich erlaubt wurde. So kann ein
            // fremdes Programm, das den Port belegt, keinen beliebigen Code in Blockbench ausführen.
            if (localStorage.getItem('claude_bridge_allow_eval') !== '1') {
                throw new Error('eval_js ist in Blockbench gesperrt. Freigeben in der Blockbench-Konsole mit: ' +
                    "localStorage.setItem('claude_bridge_allow_eval', '1')");
            }
            // eslint-disable-next-line no-new-func
            const result = new Function(code)();
            return { result: result === undefined ? null : JSON.parse(JSON.stringify(result)) };
        },
    };

    // ------------------------------------------------------------------ Verbindung

    function setStatus(text) {
        if (statusAction) statusAction.name = 'Claude Bridge: ' + text;
        console.log('[Claude Bridge] ' + text);
    }

    function connect(port) {
        if (stopped) return;
        try {
            socket = new WebSocket('ws://127.0.0.1:' + port);
        } catch (e) {
            scheduleReconnect(port);
            return;
        }
        socket.onopen = () => {
            setStatus('verbunden');
            socket.send(JSON.stringify({ type: 'hello', blockbench: Blockbench.version }));
        };
        socket.onclose = () => {
            setStatus('getrennt, versuche erneut …');
            scheduleReconnect(port);
        };
        socket.onerror = () => { /* onclose folgt */ };
        socket.onmessage = async event => {
            let msg;
            try {
                msg = JSON.parse(event.data);
            } catch (e) {
                return;
            }
            const handler = commands[msg.command];
            try {
                if (!handler) throw new Error('Unbekannter Befehl: ' + msg.command);
                const result = await handler(msg.args || {});
                socket.send(JSON.stringify({ id: msg.id, ok: true, result }));
            } catch (e) {
                socket.send(JSON.stringify({ id: msg.id, ok: false, error: String(e && e.message || e) }));
            }
        };
    }

    function scheduleReconnect(port) {
        if (stopped || reconnectTimer) return;
        reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            connect(port);
        }, 2000);
    }

    function start(port) {
        stopped = false;
        connect(port || DEFAULT_PORT);
    }

    function stop() {
        stopped = true;
        if (reconnectTimer) clearTimeout(reconnectTimer);
        if (socket) socket.close();
    }

    // Für den Headless-Betrieb (tools/blockbench-mcp/headless.js) und zum Testen.
    window.ClaudeBridge = { start, stop, commands };

    if (typeof Plugin !== 'undefined' && Plugin.register) {
        Plugin.register(PLUGIN_ID, {
            title: 'Claude Bridge',
            author: 'Strata Team',
            description: 'Lässt Claude über MCP Modelle in Blockbench bauen, texturieren, animieren und exportieren.',
            icon: 'smart_toy',
            version: '0.1.0',
            variant: 'both',
            onload() {
                statusAction = new Action('claude_bridge_status', {
                    name: 'Claude Bridge: startet …',
                    icon: 'smart_toy',
                    click() {
                        Blockbench.showQuickMessage(statusAction.name, 2000);
                    },
                });
                MenuBar.addAction(statusAction, 'tools');
                start(DEFAULT_PORT);
            },
            onunload() {
                stop();
                if (statusAction) statusAction.delete();
            },
        });
    }
})();
