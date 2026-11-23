// AdminCentral (Vaadin) helpers for the UI features: the features call these steps, so they read as user actions.
// Elements are found by their visible text, as an editor sees them; the browser is Karate's driver (karate.driver).
function fn() {
  var d = function () { return karate.driver; };
  var str = function (s) { return JSON.stringify(s); };
  // The first visible element matching css whose text contains text (JS run in the page)
  var visible = function (css, text) {
    return 'Array.from(document.querySelectorAll(' + str(css) + ')).find(function (e) {'
        + ' return e.offsetParent !== null && e.innerText.indexOf(' + str(text) + ') >= 0; })';
  };
  // A real mouse click (Karate's mouse, CDP input events) on the first visible element matching css with text:
  // Vaadin widgets ignore a scripted element.click(). The element is marked to get a unique selector
  var clickVisible = function (css, text) {
    d().waitUntil('!!' + visible(css, text));
    d().script('(function () { document.querySelectorAll("[data-lv-it-click]").forEach(function (e) {'
        + ' e.removeAttribute("data-lv-it-click"); }); ' + visible(css, text) + '.setAttribute("data-lv-it-click", "1"); })()');
    d().mouse('[data-lv-it-click]').click();
  };
  var texts = function (css) {
    return d().script('Array.from(document.querySelectorAll(' + str(css) + '))'
        + '.filter(function (e) { return e.offsetParent !== null; })'
        + '.map(function (e) { return e.innerText.trim().replace(/\\s+/g, " "); })');
  };
  // A real mouse click at a point of the page (CDP input events)
  var clickAt = function (point) {
    ['mouseMoved', 'mousePressed', 'mouseReleased'].forEach(function (type) {
      d().getCdpClient().method('Input.dispatchMouseEvent').param('type', type).param('x', point.x).param('y', point.y)
          .param('button', type === 'mouseMoved' ? 'none' : 'left').param('buttons', type === 'mousePressed' ? 1 : 0)
          .param('clickCount', 1).send();
    });
  };
  var ui = {};

  ui.login = function () {
    d().setUrl(author + '/.magnolia/admincentral');
    d().waitFor('input[name=mgnlUserPSWD]');
    d().input('input[name=mgnlUserId]', mgnlUser);
    d().input('input[name=mgnlUserPSWD]', mgnlPassword);
    d().script("document.querySelector('input[name=mgnlUserPSWD]').form.submit()");
    d().waitFor('.v-app');
    // The first superuser login of a fresh instance shows the privacy notice about usage statistics (once per run,
    // ResurfaceUIPresenter.showOnFirstLogin): it blocks the apps until "OK" is clicked
    karate.pause(3000);
    if (d().script('!!' + visible('body *', 'Help make Magnolia better'))) {
      clickVisible('.v-button', 'OK');
      d().waitUntil('!' + visible('body *', 'Help make Magnolia better'));
    }
  };

  // Opens the browser sub-app of app at path, through the URL fragment (the AdminCentral follows it; a page load would
  // not change app). The tree expands to the item and selects it: done when the selected row is at the path's depth
  // (each scenario logs in anew, and the steps of a scenario always change depth).
  // options.selects = false: Magnolia is expected not to select anything (a path the tree doesn't show), so it only
  // waits for the app to be there; '/' = the root, nothing selected
  ui.openBrowser = function (app, path, options) {
    var selects = path !== '/' && !(options && options.selects === false);
    d().script('window.location.hash = ' + str('app:' + app + ':browser;' + path + ':treeview:'));
    d().waitUntil("!!document.querySelector('.v-actionbar') && document.querySelectorAll('tr.v-treegrid-row').length > 0");
    if (selects) {
      var depth = path.split('/').length - 2; // '/a' = 0, '/a/b' = 1
      d().waitUntil("!!document.querySelector('tr.v-treegrid-row-selected.v-treegrid-row-depth-" + depth + "')");
    }
    karate.pause(selects ? 1500 : 3000); // the action bar updates after the selection
  };

  ui.selectedRow = function () { return texts('tr.v-treegrid-row-selected').join(' '); };
  // Depth in the tree of the selected row: 0 = top level, 2 = a variant (original / localeVariants / variant)
  ui.selectedRowDepth = function () {
    return d().script("(function () { var r = document.querySelector('tr.v-treegrid-row-selected');"
        + " var m = r && r.className.match(/v-treegrid-row-depth-(\\d+)/); return m ? parseInt(m[1]) : -1; })()");
  };
  // After an action that selects another row (e.g. a new variant): the Pages tree takes a moment longer
  ui.waitForSelectedRowDepth = function (depth) {
    d().waitUntil("!!document.querySelector('tr.v-treegrid-row-selected.v-treegrid-row-depth-" + depth + "')");
  };
  ui.treeRows = function () { return texts('tr.v-treegrid-row'); };
  // --- Browser columns and filters ---

  // The cell of the selected row in the column with this header: its text, and its Magnolia icon if it has one
  var selectedCell = function (column, read) {
    return d().script("(function () { var headers = Array.from(document.querySelectorAll('th'));"
        + " var i = headers.findIndex(function (h) { return h.offsetParent !== null && h.innerText.trim() === " + str(column) + "; });"
        + " var row = document.querySelector('tr.v-treegrid-row-selected');"
        + " var td = i < 0 || !row ? null : row.querySelectorAll('td')[i]; if (!td) return null; " + read + " })()");
  };
  ui.selectedCell = function (column) { return selectedCell(column, "return td.innerText.trim();"); };
  ui.selectedCellIcon = function (column) {
    return selectedCell(column, "var icon = td.querySelector('[class*=icon-]');"
        + " var m = icon && icon.className.match(/(?:^|\\s)(icon-[\\w-]+)/); return m ? m[1] : '';");
  };
  // The tooltip an editor gets with the mouse over that cell ('' if none shows)
  ui.selectedCellTooltip = function (column) {
    var point = selectedCell(column, "var r = td.getBoundingClientRect(); return { x: r.left + 12, y: r.top + r.height / 2 };");
    var move = function (x, y) {
      d().getCdpClient().method('Input.dispatchMouseEvent').param('type', 'mouseMoved').param('x', x).param('y', y)
          .param('button', 'none').param('buttons', 0).send();
    };
    move(point.x - 3, point.y); move(point.x, point.y);
    karate.pause(1500); // Vaadin shows a tooltip after a short delay
    var tooltip = d().script("(function () { var t = document.querySelector('.v-tooltip');"
        + " return t && t.offsetParent !== null && parseInt(t.style.left) >= 0 ? t.innerText.trim() : ''; })()");
    move(5, 5);
    return tooltip;
  };
  // Names of the rows on screen (the "Name" column)
  ui.rowNames = function () {
    return d().script("Array.from(document.querySelectorAll('tr.v-treegrid-row'))"
        + ".filter(function (r) { return r.offsetParent !== null && r.querySelectorAll('td').length > 1; })"
        + ".map(function (r) { return r.querySelectorAll('td')[1].innerText.trim(); })");
  };
  // "Add filter" above the tree: picks the filter, then one of its options
  ui.addFilter = function (filter, option) {
    // The widget is wider than what shows of it (its slot clips it): a click in the middle of the visible part
    var box = d().script("(function () { var r = document.querySelector('.v-slot-add-filter').getBoundingClientRect();"
        + " return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; })()");
    clickAt(box);
    clickVisible('.v-filterselect-suggestpopup .gwt-MenuItem', filter);
    clickVisible('.v-filterselect-suggestpopup .gwt-MenuItem', option);
    d().waitUntil('!!' + visible('.removable-filter-container .v-button-caption', filter + ': ' + option));
    karate.pause(2500); // the rows reload
  };
  // The filters in use, as shown: "Localized: Has language variants"
  ui.filters = function () {
    return texts('.removable-filter-container .v-button-caption').filter(function (t) { return t.length > 0; });
  };
  ui.removeFilters = function () {
    while (d().script('!!' + visible('.removable-filter-container .close-button', ''))) {
      clickVisible('.removable-filter-container .close-button', '');
      karate.pause(2500);
    }
  };

  // --- Page editor, with the demo's front end in its iframe (same origin: read from the page, no frame switch) ---

  var frontEnd = "Array.from(document.querySelectorAll('iframe')).find(function (f) { try {"
      + " return f.contentWindow.location.href.indexOf('/webresources/frontend/') >= 0; } catch (e) { return false; } })";
  // Clicks an element of the front end: find is JS that gives the element, with "doc" = the front end's document
  var clickInFrontEnd = function (find) {
    var point = "(function () { var f = " + frontEnd + "; if (!f) return null; var doc = f.contentDocument; var e = " + find + ";"
        + " if (!e) return null; e.scrollIntoView({ block: 'center' }); var a = f.getBoundingClientRect(), b = e.getBoundingClientRect();"
        + " return { x: a.left + b.left + b.width / 2, y: a.top + b.top + b.height / 2 }; })()";
    d().waitUntil('!!' + point);
    clickAt(d().script(point));
    karate.pause(2000);
  };
  // Opens the page at path in the page editor: done when the front end shows that page with the editor's bars
  ui.openPageEditor = function (path) {
    d().script('window.location.hash = ' + str('app:pages-app:detail;' + path + ':edit'));
    d().waitUntil("(function () { var f = " + frontEnd + ";"
        + " return !!f && f.contentWindow.location.search.indexOf(" + str('path=' + path + '&') + ") >= 0"
        + " && f.contentDocument.querySelectorAll('.mgnlEditorBar').length > 0; })()");
    karate.pause(1500);
  };
  // What the front end shows: { query: what the page editor asked it for, title, texts: the text components }
  ui.pageEditor = function () {
    return d().script("(function () { var f = " + frontEnd + "; var doc = f.contentDocument;"
        + " return { query: decodeURIComponent(f.contentWindow.location.search), title: doc.getElementById('title').innerText.trim(),"
        + " texts: Array.from(doc.querySelectorAll('p.text')).map(function (e) { return e.innerText.trim(); }) }; })()");
  };
  ui.waitForPageEditorText = function (text) {
    d().waitUntil("(function () { var f = " + frontEnd + "; return !!f && Array.from(f.contentDocument.querySelectorAll('p.text'))"
        + ".some(function (e) { return e.innerText.trim() === " + str(text) + "; })"
        + " && f.contentDocument.querySelectorAll('.mgnlEditorBar').length > 0; })()");
    karate.pause(1500);
  };
  // Values of the drop-downs on screen: in the page editor, the language switch (none on a page variant)
  ui.dropDowns = function () {
    return d().script("Array.from(document.querySelectorAll('.v-filterselect input'))"
        + ".filter(function (e) { return e.offsetParent !== null; }).map(function (e) { return e.value; })");
  };
  // Clicks the area's "New ... Component" bar: Magnolia opens the "Add component" dialog
  ui.addComponent = function () {
    clickInFrontEnd("Array.from(doc.querySelectorAll('.mgnlEditorBar.component'))"
        + ".filter(function (e) { return e.innerText.indexOf('New') >= 0; }).pop()");
    d().waitFor('.v-window');
  };
  // Selects the text component showing this text: the action bar then has the component's actions
  ui.selectComponent = function (text) {
    clickInFrontEnd("Array.from(doc.querySelectorAll('p.text')).find(function (e) { return e.innerText.trim() === " + str(text) + "; })");
  };
  // Picks an option of the n-th drop-down (from 0) of the open dialog
  ui.dialogSelect = function (index, option) {
    d().script("(function () { document.querySelectorAll('[data-lv-it-select]').forEach(function (e) { e.removeAttribute('data-lv-it-select'); });"
        + " document.querySelectorAll('.v-window .v-filterselect .v-filterselect-button')[" + index + "].setAttribute('data-lv-it-select', '1'); })()");
    d().mouse('[data-lv-it-select]').click();
    clickVisible('.v-filterselect-suggestpopup .gwt-MenuItem', option);
    karate.pause(500);
  };

  // Visible action bar buttons, in order, " (disabled)" when greyed out
  ui.actions = function () {
    return d().script("Array.from(document.querySelectorAll('.v-actionbar .v-action'))"
        + ".filter(function (e) { return e.offsetParent !== null; })"
        + ".map(function (e) { return e.innerText.trim() + (e.classList.contains('v-disabled') ? ' (disabled)' : ''); })");
  };
  ui.clickAction = function (label) { clickVisible('.v-actionbar .v-action', label); };

  // Double-click on the selected row: Magnolia runs the action bar's default action, if available on the item
  ui.doubleClickSelectedRow = function () {
    d().mouse('tr.v-treegrid-row-selected td').doubleClick();
    karate.pause(3000);
  };
  // Where the AdminCentral is: "app:<app>:<subApp>;<path>:<view>"
  ui.location = function () { return d().script("decodeURIComponent(window.location.hash).replace(/^#/, '')"); };

  // The middle of the name cell of a visible tree row: row = { name: <text of the name cell>, depth: <0 = top level> }
  // (depth is optional; a variant shows its original's name, so it is told apart by its depth, 2)
  var rowPoint = function (row) {
    var rows = 'tr.v-treegrid-row' + (row.depth === undefined ? '' : '-depth-' + row.depth);
    var find = '(function () { var td = Array.from(document.querySelectorAll(' + str(rows + ' td')
        + ')).find(function (e) { return e.offsetParent !== null && e.innerText.trim() === ' + str(row.name) + '; });'
        + ' if (!td) return null; var r = td.getBoundingClientRect();'
        + ' return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; })()';
    d().waitUntil('!!' + find);
    return d().script(find);
  };
  // Drags a tree row and drops it on another one, as an editor does: rows as for rowPoint.
  // The tree uses the browser's native drag and drop (HTML5), which Karate's mouse can't drive: the drag is started
  // with the mouse, intercepted, and carried on with Chromium's own drag events (CDP Input.dispatchDragEvent), the
  // way Playwright does it. Returns false if the browser didn't start a drag
  ui.drag = function (from, to) {
    var cdp = d().getCdpClient();
    var mouse = function (type, point, buttons) {
      cdp.method('Input.dispatchMouseEvent').param('type', type).param('x', point.x).param('y', point.y)
          .param('button', buttons === 0 && type === 'mouseMoved' ? 'none' : 'left').param('buttons', buttons).param('clickCount', 1).send();
    };
    var dragEvent = function (type, point, data) {
      cdp.method('Input.dispatchDragEvent').param('type', type).param('x', point.x).param('y', point.y)
          .param('data', data).send();
    };
    // Magnolia moves the selected rows: select the dragged one first.
    // The moves with the button down name the button too: without it Chromium doesn't start a drag
    var source = rowPoint(from);
    mouse('mouseMoved', source, 0);
    mouse('mousePressed', source, 1);
    mouse('mouseReleased', source, 0);
    karate.pause(1500);
    source = rowPoint(from);
    var target = rowPoint(to);
    var data = null;
    var onDrag = function (event) { data = event.get('data'); };
    cdp.on('Input.dragIntercepted', onDrag);
    cdp.method('Input.setInterceptDrags').param('enabled', true).send();
    mouse('mouseMoved', source, 0);
    mouse('mousePressed', source, 1);
    mouse('mouseMoved', { x: source.x + 6, y: source.y + 6 }, 1);
    mouse('mouseMoved', { x: (source.x + target.x) / 2, y: (source.y + target.y) / 2 }, 1);
    for (var i = 0; i < 20 && data === null; i++) { karate.pause(100); }
    cdp.method('Input.setInterceptDrags').param('enabled', false).send();
    cdp.off('Input.dragIntercepted', onDrag);
    if (data === null) {
      mouse('mouseReleased', target, 0);
      return false;
    }
    dragEvent('dragEnter', target, data);
    dragEvent('dragOver', target, data);
    karate.pause(300);
    dragEvent('dragOver', target, data);
    dragEvent('drop', target, data);
    mouse('mouseReleased', target, 0);
    karate.pause(800); // the round trip to the server: the move, or the notification that it is not allowed
    return true;
  };
  // Texts of the notifications on screen
  // (not through texts(): a notification is position: fixed, so it has no offsetParent)
  ui.notifications = function () {
    return d().script("Array.from(document.querySelectorAll('.v-Notification')).map(function (e) { return e.innerText.trim(); })");
  };

  // Checkboxes of the open dialog: "<label> [x]" checked, "[ ]" not, " disabled" if it can't be changed
  ui.dialogCheckboxes = function () {
    d().waitFor('.v-window .v-checkbox');
    return d().script("Array.from(document.querySelectorAll('.v-window .v-checkbox')).map(function (e) {"
        + " var input = e.querySelector('input');"
        + " return e.innerText.trim() + ' [' + (input.checked ? 'x' : ' ') + ']' + (input.disabled ? ' disabled' : ''); })");
  };
  ui.check = function (label) { clickVisible('.v-window .v-checkbox label', label); };
  ui.clickDialogButton = function (label) { clickVisible('.v-window .v-button', label); };
  // Text of the open (Vaadin) dialog
  ui.dialogText = function () {
    d().waitFor('.v-window');
    return d().script("document.querySelector('.v-window').innerText.replace(/\\s+/g, ' ').trim()");
  };
  // Values of the text fields of the open (Vaadin) dialog, in order
  ui.dialogValues = function () {
    d().waitFor('.v-window input');
    return d().script("Array.from(document.querySelectorAll('.v-window input[type=text]')).map(function (e) { return e.value; })");
  };
  // Replaces the value of the n-th text field (from 0) of the open dialog, typing as an editor does
  ui.dialogType = function (index, value) {
    d().script("(function () { document.querySelectorAll('[data-lv-it-input]').forEach(function (e) { e.removeAttribute('data-lv-it-input'); });"
        + " var e = document.querySelectorAll('.v-window input[type=text]')[" + index + "]; e.setAttribute('data-lv-it-input', '1'); e.focus(); e.select(); })()");
    d().input('[data-lv-it-input]', value);
    // Vaadin sends the value when the field loses the focus
    d().script("document.querySelector('[data-lv-it-input]').blur()");
    karate.pause(500);
  };
  ui.waitForDialogClosed = function () { d().waitUntil("!document.querySelector('.v-window')"); };
  ui.dialogOpen = function () {
    karate.pause(1000);
    return d().script("!!document.querySelector('.v-window')");
  };

  // --- Magnolia's newer dialogs and the detail form: a React front end ("Warp") in an iframe ---

  // Runs fn inside the Warp iframe on screen, then goes back to the page. Karate's frame indexes are not in the
  // page's order, so the frame is found by its URL
  var inWarpFrame = function (fn) {
    for (var attempt = 0; attempt < 20; attempt++) {
      for (var i = 0; i < 4; i++) {
        var found = false;
        try {
          d().switchFrame(i);
          found = d().script("location.href.indexOf('warp-frontend') >= 0 && document.querySelectorAll('button').length > 0");
        } catch (e) {
          found = false;
        }
        if (found) {
          try {
            return fn();
          } finally {
            d().switchFrame(null);
          }
        }
        try { d().switchFrame(null); } catch (e) { /* no such frame: already on the page */ }
      }
      karate.pause(500);
    }
    karate.fail('no Warp dialog or form on screen');
  };
  var frameButton = function (label) {
    return "Array.from(document.querySelectorAll('button')).find(function (e) { return e.innerText.trim().indexOf("
        + str(label) + ") === 0; })";
  };
  // Text of the confirmation dialog on screen (e.g. "Mark this item for deletion? ...")
  ui.confirmationText = function () {
    return inWarpFrame(function () { return d().script("document.body.innerText.replace(/\\s+/g, ' ').trim()"); });
  };
  // Clicks the button of the confirmation dialog whose label starts with label
  ui.confirm = function (label) {
    inWarpFrame(function () {
      d().waitUntil('!!' + frameButton(label));
      d().script(frameButton(label) + '.click()');
    });
    karate.pause(4000); // the action runs on the server, then the tree and the action bar update
  };
  // The detail form of the item being edited: value of the field with this id (the property name)
  ui.formValue = function (field) {
    return inWarpFrame(function () { return d().script('document.getElementById(' + str(field) + ').value'); });
  };
  // Replaces the value of a text field of the detail form, typing as an editor does
  ui.formType = function (field, value) {
    inWarpFrame(function () {
      d().script('(function () { var e = document.getElementById(' + str(field) + '); e.focus(); e.select(); })()');
      d().input('#' + field, value);
    });
  };
  // true if the field of the detail form can't be changed
  ui.formReadOnly = function (field) {
    return inWarpFrame(function () {
      return d().script('(function () { var e = document.getElementById(' + str(field) + '); return e.readOnly || e.disabled; })()');
    });
  };
  // Labels of the fields of the detail form, in order
  ui.formLabels = function () {
    return inWarpFrame(function () {
      return d().script("Array.from(document.querySelectorAll('label')).map(function (e) { return e.innerText.trim(); })");
    });
  };
  // Languages offered by the detail form's language switch: [] if the form has none
  ui.formLanguages = function () {
    return inWarpFrame(function () {
      return d().script("Array.from(document.querySelectorAll('select option')).map(function (e) { return e.innerText.trim(); })");
    });
  };
  ui.formSave = function () {
    inWarpFrame(function () { d().script(frameButton('Save changes') + '.click()'); });
    karate.pause(4000);
  };

  // --- Downloads (export) ---

  // From now on the browser saves downloads in a new empty folder: ui.downloads() lists the files in it
  var downloadDir = null;
  ui.catchDownloads = function () {
    downloadDir = '/tmp/lv-it-downloads-' + java.lang.System.nanoTime();
    new java.io.File(downloadDir).mkdirs();
    d().getCdpClient().browserMethod('Browser.setDownloadBehavior').param('behavior', 'allow')
        .param('downloadPath', downloadDir).send();
  };
  ui.downloads = function () {
    karate.pause(4000);
    return karate.toJson(new java.io.File(downloadDir).list());
  };
  ui.downloadedText = function (name) {
    return new java.lang.String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(downloadDir, name)), 'UTF-8') + '';
  };

  // Fails if no notification containing text shows up within the driver timeout
  ui.waitForNotification = function (text) {
    d().waitUntil('!!' + visible('.v-Notification-caption', text));
  };

  // Fails if Magnolia's red error banner (an action that failed) doesn't show text within the driver timeout
  ui.waitForErrorBanner = function (text) {
    d().waitUntil('!!' + visible('.v-verticallayout-banner.error', text));
  };

  // Embeds a screenshot of the page in the Karate report, as evidence of what the step saw
  ui.screenshot = function () { d().screenshot(true); };

  return ui;
}
