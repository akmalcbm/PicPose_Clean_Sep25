<?php
// includes/footer.php - closes layout wrapper and loads scripts
// Replaces the previous footer. Keeps Bootstrap, CKEditor, sidebar toggle and adds
// a safe "force full navigation" fallback for sidebar links to prevent stale content.
?>
    </main> <!-- close main-content opened in header -->
</div> <!-- close .d-flex wrapper -->

<!-- Bootstrap 5 JS bundle (includes Popper & modal) - required for modals, tooltips, dropdowns, etc. -->
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js" integrity="" crossorigin="anonymous"></script>

<!-- CKEditor 5 (load once globally) -->
<script src="https://cdn.ckeditor.com/ckeditor5/39.0.1/classic/ckeditor.js"></script>

<script>
/**
 * Ensure Bootstrap is available; some pages rely on bootstrap.Modal being present.
 */
(function(){
    if (typeof window.bootstrap === 'undefined' || !window.bootstrap.Modal) {
        console.warn('Bootstrap JS not detected. Modals and other Bootstrap JS components will not work. Ensure bootstrap.bundle is loaded in footer.');
    }
})();
</script>

<script>
/**
 * CKEditor 5 setup
 * - One editor instance per textarea
 * - Syncs content back to textarea BEFORE form submit
 */
window.PicPoseEditors = {};

document.addEventListener('DOMContentLoaded', function () {

    const editorIds = ['privacy_editor', 'terms_editor', 'about_editor', 'content_editor', 'editor'];

    editorIds.forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;

        ClassicEditor.create(el).then(editor => {
            window.PicPoseEditors[id] = editor;
        }).catch(err => {
            console.error('CKEditor init failed for', id, err);
        });
    });

    // 🔥 CRITICAL: Sync editor data to textarea before submit
    const form = document.querySelector('form[action="update_settings.php"]');
    if (form) {
        form.addEventListener('submit', function () {
            Object.keys(window.PicPoseEditors).forEach(id => {
                const textarea = document.getElementById(id);
                if (textarea) {
                    textarea.value = window.PicPoseEditors[id].getData();
                }
            });
        });
    }
});
</script>

<script>
/**
 * Sidebar toggle behavior for mobile:
 * - Opens/closes the sidebar when hamburger clicked
 * - Closes when clicking overlay, pressing ESC, or tapping a sidebar link (on small screens)
 * - Updates aria-expanded attribute on toggle button(s)
 */
(function(){
    var sidebar = document.querySelector('.sidebar');
    var overlay = document.getElementById('sidebar-overlay');
    var toggles = document.querySelectorAll('.sidebar-toggle');

    if (!sidebar || !overlay || toggles.length === 0) return;

    function openSidebar() {
        sidebar.classList.add('open');
        overlay.classList.add('visible');
        document.body.classList.add('sidebar-open');
        toggles.forEach(function(b){ b.setAttribute('aria-expanded','true'); });
        overlay.setAttribute('aria-hidden','false');
        // focus the first focusable element inside sidebar for accessibility
        var firstLink = sidebar.querySelector('a, button, input, [tabindex]');
        if (firstLink) firstLink.focus();
    }

    function closeSidebar() {
        sidebar.classList.remove('open');
        overlay.classList.remove('visible');
        document.body.classList.remove('sidebar-open');
        toggles.forEach(function(b){ b.setAttribute('aria-expanded','false'); });
        overlay.setAttribute('aria-hidden','true');
        if (toggles[0]) toggles[0].focus();
    }

    toggles.forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            if (sidebar.classList.contains('open')) closeSidebar();
            else openSidebar();
        });
    });

    overlay.addEventListener('click', function() {
        closeSidebar();
    });

    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') closeSidebar();
    });

    // Close the sidebar automatically when a link is clicked (mobile)
    sidebar.querySelectorAll('a').forEach(function(link) {
        link.addEventListener('click', function() {
            if (window.innerWidth < 992) {
                closeSidebar();
            }
        });
    });

    // Optional: close when window is resized to large screens
    window.addEventListener('resize', function() {
        if (window.innerWidth >= 992) {
            // ensure sidebar is visible (desktop layout)
            sidebar.classList.remove('open'); // desktop version uses visible sidebar in layout
            overlay.classList.remove('visible');
            document.body.classList.remove('sidebar-open');
            toggles.forEach(function(b){ b.setAttribute('aria-expanded','false'); });
            overlay.setAttribute('aria-hidden','true');
        }
    });
})();
</script>

<!-- Force full-page navigation for sidebar links (fallback for any client-side interception) -->
<script>
document.addEventListener('DOMContentLoaded', function() {
    try {
        var sidebar = document.querySelector('#sidebar');
        if (!sidebar) return;
        var links = sidebar.querySelectorAll('a.nav-link, a.btn-post, a');

        links.forEach(function(a) {
            // Use capture so we run before other handlers
            a.addEventListener('click', function(ev) {
                try {
                    var href = a.getAttribute('href') || '';
                    // allow modifier / new tab / javascript pseudo-links / anchors
                    if (!href || href === '#' || href.startsWith('javascript:')) return;
                    if (a.target && a.target === '_blank') return;
                    if (ev.metaKey || ev.ctrlKey || ev.shiftKey || ev.altKey) return;

                    // If the browser or another handler already prevented default, we still force navigation
                    // Small delay to allow sidebar close animation to run smoothly.
                    ev.preventDefault();
                    setTimeout(function(){ window.location.href = href; }, 80);
                } catch (innerErr) {
                    // don't let this break any other scripts
                    console.error('Sidebar forced navigation error', innerErr);
                }
            }, { capture: true });
        });

        // Optional debug: log when forced nav occurs (comment out in production)
        window.addEventListener('unhandledrejection', function(e){ console.warn('Unhandled promise rejection', e); });
    } catch (err) {
        console.error('Force navigation init error', err);
    }
});
</script>



</body>
</html>