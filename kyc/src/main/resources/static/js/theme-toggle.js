(function() {
    var btn = document.getElementById('theme-toggle');
    function updateIcon() {
        var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        btn.querySelector('.icon-sun').style.display = isDark ? 'none' : 'block';
        btn.querySelector('.icon-moon').style.display = isDark ? 'block' : 'none';
    }
    updateIcon();
    btn.addEventListener('click', function() {
        var current = document.documentElement.getAttribute('data-theme');
        var next = current === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('peppol.theme.v1', next);
        updateIcon();
    });
})();
